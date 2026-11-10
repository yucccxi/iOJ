package me.iynotuo.judge.judge.mapper;

import me.iynotuo.judge.judge.entity.JudgeNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface JudgeNodeMapper {

    JudgeNode findById(@Param("id") String id);

    List<JudgeNode> findAll();

    int insert(JudgeNode node);

    int updateHeartbeat(JudgeNode node);

    int markOfflineBefore(@Param("threshold") LocalDateTime threshold);
}
