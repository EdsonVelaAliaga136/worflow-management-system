package com.evela.sigotrab.adapters.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
//@RequestMapping("/dashboard")
public class DashboardController {

    /*@Autowired
    private UserJpaRepository userJpaRepository;*/

    @GetMapping("/dashboard")
    public String dashboard(){
        return "dashboard";
    }
}
