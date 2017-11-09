package com.minitwit;

import com.minitwit.config.WebConfig;
import com.minitwit.service.impl.MiniTwitService;
import org.hydrogen.jetty.Jetty;

public class App {
	public static void main(String[] args) {
        Jetty.start(WebConfig.of(new MiniTwitService())).join();
    }
}
