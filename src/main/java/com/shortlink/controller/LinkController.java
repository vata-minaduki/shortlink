package com.shortlink.controller;

import com.shortlink.common.BusinessException;
import com.shortlink.common.Result;
import com.shortlink.entity.ShortLink;
import com.shortlink.mapper.LinkMapper;
import com.shortlink.util.Base62;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.UUID;

@RestController
public class LinkController {

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

        return Result.ok(new CreateResponse(code, "http://127.0.0.1:8080/" + code));
    }

    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        ShortLink link = linkMapper.findByCode(code);

        if (link == null || link.getStatus() == null || link.getStatus() == 0) {
            return ResponseEntity.notFound().build();
        }
        if (link.getExpireAt() != null && link.getExpireAt().isBefore(LocalDateTime.now())) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(link.getLongUrl()))
                .build();
    }

    @PostMapping("/api/links/{code}/disable")
    public Result<Void> disable(@PathVariable String code){

        linkMapper.disable(code);

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
}