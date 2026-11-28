package com.shortlink.controller;

import com.shortlink.common.BusinessException;
import com.shortlink.common.Result;
import com.shortlink.entity.DailyClick;
import com.shortlink.entity.ShortLink;
import com.shortlink.mapper.LinkMapper;
import com.shortlink.util.Base62;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
public class LinkController {

    private static final Logger log = LoggerFactory.getLogger(LinkController.class);
    private final LinkMapper linkMapper;

    public LinkController(LinkMapper linkMapper) {
        this.linkMapper = linkMapper;
    }

    public static class CreateRequest {
        private String longUrl;
        public String getLongUrl() { return longUrl; }
        public void setLongUrl(String longUrl) { this.longUrl = longUrl; }
    }

    public static class CreateResponse {
        private final String code;
        private final String shortUrl;
        public CreateResponse(String code, String shortUrl) {
            this.code = code;
            this.shortUrl = shortUrl;
        }
        public String getCode() { return code; }
        public String getShortUrl() { return shortUrl; }
    }

    @PostMapping("/api/links")
    @Transactional
    public Result<CreateResponse> create(@RequestBody CreateRequest req) {
        if (req.getLongUrl() == null || req.getLongUrl().isBlank()) {
            throw new IllegalArgumentException("longUrl 不能为空");
        }

        //先插占位码，换回自增 id
        ShortLink link = new ShortLink();
        link.setCode("t" + UUID.randomUUID().toString().replace("-", "").substring(0, 9));
        link.setLongUrl(req.getLongUrl());
        linkMapper.insert(link);

        //用 id 生成真码
        String code = Base62.encode(link.getId());

        //回填
        linkMapper.updateCode(link.getId(), code);

        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .build().toUriString();
        return Result.ok(new CreateResponse(code, baseUrl + "/" + code));
    }

    @GetMapping("/{code:[0-9a-zA-Z]{1,10}}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        ShortLink link = linkMapper.findByCode(code);

        // 不存在 / 已禁用 / 已过期 -> 404
        if (link == null || link.getStatus() == null || link.getStatus() == 0) {
            return ResponseEntity.notFound().build();
        }
        if (link.getExpireAt() != null && link.getExpireAt().isBefore(LocalDateTime.now())) {
            return ResponseEntity.notFound().build();
        }

        // 构造 URI，失败就给明确错误
        URI uri;
        try {
            uri = URI.create(link.getLongUrl());
            if (!uri.isAbsolute()
                    || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))) {
                throw new IllegalArgumentException("只允许 http/https 绝对地址");
            }
        } catch (IllegalArgumentException e) {
            log.warn("短链原地址不合法: code={}, url={}", code, link.getLongUrl());
            throw new BusinessException(500, "该短链的原地址不合法");
        }

        //计数
        try {
            linkMapper.increaseClick(code);
        } catch (Exception e) {
            log.warn("记录点击失败: code={}", code, e);
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(link.getLongUrl()))
                .build();
    }

    @PostMapping("/api/links/{code}/disable")
    public Result<Void> disable(@PathVariable String code){
        int rows = linkMapper.disable(code);
        if (rows == 0) throw new BusinessException(404,"短码不存在");
        return Result.ok(null);
    }

    @GetMapping("/api/links/{code}")
    public Result<ShortLink> detail(@PathVariable String code){
        ShortLink shortLink = linkMapper.findByCode(code);
        if(shortLink == null){
            throw new BusinessException(404,"短码不存在");
        }
        return Result.ok(shortLink);
    }

    public record StatsResponse(long total,List<DailyClick> daily){}
    @GetMapping("/api/links/{code}/stats")
    public Result<StatsResponse> stats(@PathVariable String code){
        List<DailyClick> daily = linkMapper.findClicks(code);
        long total = daily.stream()
                .mapToLong(DailyClick::getClickCount)
                .sum();
        return Result.ok(new StatsResponse(total,daily));
    }

    public record PageResponse<T>(int page, int size, long total, List<T> items) {}
    @GetMapping("/api/links")
    public Result<PageResponse<ShortLink>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (page < 1) page = 1;
        if (size < 1) size = 20;
        if (size > 100) size = 100;            // 防止一次拉全表

        int offset = (page - 1) * size;
        List<ShortLink> items = linkMapper.findPage(offset, size);
        long total = linkMapper.countAll();

        return Result.ok(new PageResponse<>(page, size, total, items));
    }
}