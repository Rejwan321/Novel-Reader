package com.reader.Novel.Reader;

//import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WebNovel {

    @GetMapping("/hello")
    public String hello(){
        return "Hello Jethu Nigga!";
    }

//    @PreAuthorize("hasRole('READER')")
    @GetMapping("/reader")
    public String reader(){return "Hello, Reader!";}
}
