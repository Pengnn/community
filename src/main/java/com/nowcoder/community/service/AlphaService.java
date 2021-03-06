package com.nowcoder.community.service;

import com.nowcoder.community.dao.AlphaDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@Service
//@Scope("prototype")//多例
public class AlphaService {
    @Autowired
    private AlphaDao alphaDao;//service调用dao  业务层调用数据库层

    public AlphaService(){
        System.out.println("实例化AlphaService,调用了构造器");
    }
    @PostConstruct//构造器之后调用
    public void init(){
        System.out.println("------初始化AlphaService------");
    }
    @PreDestroy//对象销毁之前调用，释放资源
    public void destory(){
        System.out.println("销毁AlphaService...");
    }
    /*模拟查询业务*/
    public String find(){
        return alphaDao.select();
    }
}
