package me.iynotuo.judge.judge.mapper;

import me.iynotuo.judge.judge.entity.JudgeKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface JudgeKeyMapper {

    int insert(JudgeKey key);

    JudgeKey findById(@Param("id") Long id);

    JudgeKey findByHash(@Param("keyHash") String keyHash);

    List<JudgeKey> findAll();

    int updateEnabled(@Param("id") Long id, @Param("enabled") boolean enabled);

    int updateLastUsed(@Param("id") Long id, @Param("lastUsedAt") LocalDateTime lastUsedAt);

    int deleteById(@Param("id") Long id);
}
