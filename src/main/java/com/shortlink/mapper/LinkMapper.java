package com.shortlink.mapper;

import com.shortlink.entity.ShortLink;
import org.apache.ibatis.annotations.*;

@Mapper
public interface LinkMapper {

    @Insert("INSERT INTO short_link(code, long_url, created_at, expire_at, status) " +
            "VALUES(#{code}, #{longUrl}, NOW(), #{expireAt}, 1)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ShortLink link);

    @Select("SELECT id, code, long_url, created_at, expire_at, status " +
            "FROM short_link WHERE code = #{code}")
    ShortLink findByCode(@Param("code") String code);

    @Update("UPDATE short_link SET code = #{code} WHERE id = #{id}")
    int updateCode(@Param("id") Long id, @Param("code") String code);

    // 禁用
    @Update("UPDATE short_link SET status = 0 WHERE code = #{code}")
    int disable(@Param("code") String code);
}