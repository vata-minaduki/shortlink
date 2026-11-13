package com.shortlink.entity;

import java.time.LocalDateTime;

public class ShortLink {

    private Long id;
    private String code;
    private String longUrl;
    private LocalDateTime createdAt;
    private LocalDateTime expireAt;
    private Integer status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLongUrl() { return longUrl; }
    public void setLongUrl(String longUrl) { this.longUrl = longUrl; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getExpireAt() { return expireAt; }
    public void setExpireAt(LocalDateTime expireAt) { this.expireAt = expireAt; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}