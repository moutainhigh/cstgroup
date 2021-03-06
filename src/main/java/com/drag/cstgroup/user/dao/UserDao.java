package com.drag.cstgroup.user.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.drag.cstgroup.user.entity.User;

public interface UserDao extends JpaRepository<User, String>, JpaSpecificationExecutor<User> {

	@Query(value = "select * from t_user where id = ?1 ", nativeQuery = true)
	User findById(int id);
	
	@Query(value = "select * from t_user where openid = ?1 ", nativeQuery = true)
	User findByOpenid(String openid);
	
	@Query(value = "select * from t_user where parentid = ?1 ", nativeQuery = true)
	List<User> findByParentId(int uid);
	
	@Query(value = "select * from t_user where id in (?1) ", nativeQuery = true)
	List<User> findByIdIn(List<Integer> id);
}
