package com.nowcoder.community.controller;

import com.nowcoder.community.service.AlphaService;
import com.nowcoder.community.util.CommunityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import javax.management.monitor.StringMonitor;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.websocket.server.PathParam;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

@Controller
@RequestMapping("/alpha")
public class AlphaController {

    @Autowired
    private AlphaService alphaService;//controller调用service

    @RequestMapping("hello")
    @ResponseBody
    public String saveHello(){
        return "Hello Spring Boot.";
    }

    /*模拟处理查询请求*/
    @RequestMapping("/data")
    @ResponseBody
    public String getData(){
        return alphaService.find();
    }

    @RequestMapping("/http")
    public void http(HttpServletRequest request, HttpServletResponse response){
        //获取请求数据
        System.out.println(request.getMethod());//获取请求方式
        System.out.println(request.getServletPath());//请求路径
        Enumeration<String> headerNames = request.getHeaderNames();//得到请求行的所有的key
        while(headerNames.hasMoreElements()){
            String name = headerNames.nextElement();
            String value = request.getHeader(name);
            System.out.println(name+": "+value);
        }
        System.out.println(request.getParameter("code"));

        //返回相应数据
        response.setContentType("text/html;charset=utf-8");
        PrintWriter writer = null;
        try {
            writer = response.getWriter();
            writer.write("<h1>牛客网<h1>");
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            writer.close();
        }
    }

    //GET请求
    //  /students?current=1&limit=20
    @RequestMapping(path="/students",method= RequestMethod.GET)
    @ResponseBody
    public String getStudents(
            @RequestParam(name="current",required = false,defaultValue ="1") int current,
            @RequestParam(name="limit",required = false,defaultValue = "10") int limit
    ){
        System.out.println(current);
        System.out.println(limit);
        return "some students";
    }

    //  /students/123
    @RequestMapping(path = "/student/{id}",method = RequestMethod.GET)
    @ResponseBody
    public String getStudent(@PathVariable("id") int id){
        System.out.println(id);
        return "a student";
    }

    //POST 请求
    @RequestMapping(path="/student",method=RequestMethod.POST)
    @ResponseBody
    public String saveStudent(String name,int age){
        System.out.println("name="+name);
        System.out.println("age="+age);
        return "success";
    }

    //响应HTML数据
    @RequestMapping(path = "/teacher",method = RequestMethod.GET)
    public ModelAndView getTeacher(){
        ModelAndView mav=new ModelAndView();
        mav.addObject("name","张三");
        mav.addObject("age",30);
        mav.setViewName("/demo/view");
        return mav;
    }
    @RequestMapping(path="/school",method=RequestMethod.GET)
    public String getSchool(Model model){
        model.addAttribute("name","xx大学");
        model.addAttribute("age",100);
        return "/demo/view";
    }

    //响应JSON响应
    //java对象 -> JSON字符串 -> JS对象
    @RequestMapping(path="/emp",method=RequestMethod.GET)
    @ResponseBody
    public Map<String,Object> getemp(){
        Map<String,Object> emp =new HashMap<>();
        emp.put("name","张三");
        emp.put("age",22);
        emp.put("salary",8000.00);
        return emp;
    }
    @RequestMapping(path="/emps",method=RequestMethod.GET)
    @ResponseBody
    public List<Map<String,Object>> getemps(){
        List<Map<String,Object>> list=new ArrayList<>();

        Map<String,Object> emp1 =new HashMap<>();
        emp1.put("name","张三");
        emp1.put("age",22);
        emp1.put("salary",8000.00);
        list.add(emp1);
        Map<String,Object> emp2 =new HashMap<>();
        emp2.put("name","张四");
        emp2.put("age",22);
        emp2.put("salary",8000.00);
        list.add(emp2);
        Map<String,Object> emp3 =new HashMap<>();
        emp3.put("name","张五");
        emp3.put("age",22);
        emp3.put("salary",8000.00);
        list.add(emp3);
        return list;
    }

    //cookie示例
    @RequestMapping(path="/cookie/set",method = RequestMethod.GET)
    @ResponseBody
    public String setCookie(HttpServletResponse response){
        Cookie cookie = new Cookie("code", CommunityUtil.generateUUID());
        cookie.setPath("/community/alpha");
        cookie.setMaxAge(60 * 10);
        response.addCookie(cookie);
        return "set cookie";
    }
    @RequestMapping(path="/cookie/get",method = RequestMethod.GET)
    @ResponseBody
    public String getCookie(){

        return "get cookie";
    }

    //Session示例
    @RequestMapping(path="/session/set",method = RequestMethod.GET)
    @ResponseBody
    public String setSession(HttpSession session){
        session.setAttribute("id",1);
        session.setAttribute("name","Test");
        return "set session";
    }
    @RequestMapping(path="/session/get",method = RequestMethod.GET)
    @ResponseBody
    public String getSession(HttpSession session){
        System.out.println(session.getAttribute("id"));
        System.out.println(session.getAttribute("name"));
        return "get session";
    }
}
