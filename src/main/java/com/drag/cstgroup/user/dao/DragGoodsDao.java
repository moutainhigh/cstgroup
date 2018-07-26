package com.drag.cstgroup.user.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.drag.cstgroup.user.entity.ScoreGoods;


public interface DragGoodsDao extends JpaRepository<ScoreGoods, String>, JpaSpecificationExecutor<ScoreGoods> {
	
	List<ScoreGoods> findByIsEnd(int isEnd);
	
	@Query(value = "select * from drag_goods where drgoods_id = ?1", nativeQuery = true)
	ScoreGoods findGoodsDetail(int goodsId);
	
	
}
