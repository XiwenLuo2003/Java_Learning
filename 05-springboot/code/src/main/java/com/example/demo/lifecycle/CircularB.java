package com.example.demo.lifecycle;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CircularB {

    @Autowired
    private CircularA circularA;

    public CircularA getCircularA() {
        return circularA;
    }
}
