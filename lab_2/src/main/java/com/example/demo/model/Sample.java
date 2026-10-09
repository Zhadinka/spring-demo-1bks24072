package com.example.demo.model;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Sample implements Identifiable {

    private Long id;
    private String material;
    private Long customerId;
    private Instant receivedAt;
    private SampleStatus status;
}
