package com.icbc.financialinfo.modules.review;
import com.icbc.financialinfo.common.ApiResponse;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/reviews")
public class ReviewController { @GetMapping("/pending") public ApiResponse<Map<String,Integer>> pending() { return ApiResponse.ok(Map.of("initial",0,"final",0)); } }
