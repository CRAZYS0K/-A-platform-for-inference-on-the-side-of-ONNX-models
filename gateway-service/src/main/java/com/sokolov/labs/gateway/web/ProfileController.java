package com.sokolov.labs.gateway.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ProfileController {

    @GetMapping("/profile")
    public String profile(@AuthenticationPrincipal OidcUser oidcUser, Model model) {
        model.addAttribute("email", oidcUser.getEmail());
        model.addAttribute("name", oidcUser.getPreferredUsername());
        model.addAttribute("subject", oidcUser.getSubject());
        return "profile";
    }
}
