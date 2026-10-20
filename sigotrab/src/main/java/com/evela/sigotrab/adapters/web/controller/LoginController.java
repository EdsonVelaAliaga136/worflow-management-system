package com.evela.sigotrab.adapters.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.ui.Model;
import jakarta.servlet.http.HttpSession;

@Controller
//@RequestMapping("/login")
public class LoginController {

    @GetMapping("/login")
    public String login(HttpSession session, Model model){
    //public String login(@RequestParam(value = "error", required = false) String errorParam){
        /*if (errorParam!=null) {
            errorParam = errorParam.trim();
            if (errorParam.equals("badCredentials")) {
                return "redirect:/login?error=badCredentials";
            } else if (errorParam.equals("disabled")) {
                return "redirect:/login?error=disabled";
            } else if (errorParam.equals("locked")) {
                return "redirect:/login?error=locked";
            }
        }*/
        Object error = session.getAttribute("loginError");
        if(error!=null){
            model.addAttribute("loginError", error);
            session.removeAttribute("loginError");
        }
        return "login";
    }
}
