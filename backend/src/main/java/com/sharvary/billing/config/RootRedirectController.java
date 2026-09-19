package com.sharvary.billing.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** The API has no home page; send anyone who lands on the bare host to the API docs. */
@Controller
public class RootRedirectController {

    @GetMapping("/")
    public String root() {
        return "redirect:/swagger-ui.html";
    }
}
