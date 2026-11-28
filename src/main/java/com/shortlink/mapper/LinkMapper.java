package com.shortlink.mapper;

import com.shortlink.entity.DailyClick;
import com.shortlink.entity.ShortLink;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface LinkMapper {

    @Insert("INSERT INTO short_link(code, long_url, created_at, expire_at, status) " +
            "VALUES(#{code}, #{longUrl}, NOW(), #{expireAt}, 1)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ShortLink link);

    // 查询详细短码
    @Select("SELECT id, code, long_url, created_at, expire_at, status " +
            "FROM short_link WHERE code = #{code}")
    ShortLink findByCode(@Param("code") String code);

    @Update("UPDATE short_link SET code = #{code} WHERE id = #{id}")
    int updateCode(@Param("id") Long id, @Param("code") String code);

    // 禁用
    @Update("UPDATE short_link SET status = 0 WHERE code = #{code}")
    int disable(@Param("code") String code);

    // 统计
    @Insert("INSERT INTO link_click(code, click_date, click_count) " +
            "VALUES(#{code}, CURDATE(), 1) " +
            "ON DUPLICATE KEY UPDATE click_count = click_count + 1 ")
    int increaseClick(@Param("code") String code);

    // 查询统计情况
    @Select("SELECT click_date, click_count " +
            "FROM link_click "+
            "WHERE code = #{code} "+
            "ORDER BY click_date DESC ")
    List<DailyClick> findClicks(@Param("code") String code);

    @Select("SELECT id, code, long_url, created_at, expire_at, status " +
            "FROM short_link ORDER BY id DESC LIMIT #{size} OFFSET #{offset}")
    List<ShortLink> findPage(@Param("offset") int offset, @Param("size") int size);

    @Select("SELECT COUNT(*) FROM short_link")
    long countAll();
}