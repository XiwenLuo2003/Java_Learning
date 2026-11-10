package com.example.demo.user.dto;

import java.util.List;

public record PageResult<T>(long total, long pageNum, long pageSize, List<T> records) {
}
