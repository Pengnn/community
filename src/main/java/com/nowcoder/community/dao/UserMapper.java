package com.nowcoder.community.dao;

import com.nowcoder.community.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper {

    //根据id查用户
    User selectById(int id);

    //根据用户名查用户
    User selectByName(String name);

    //根据邮箱查用户
    User selectByEmail(String email);

    //增加一个用户，返回插入的行数
    int insertUser(User user);

    //更新用户状态,返回修改的条数
    int updateStatus(int id,int status);

    //更新用户头像
    int updateHeader(int id,String headerUrl);

    //更新密码
    int updatePassword(int id,String password);

}
