package cn.iocoder.yudao.module.hr.api.test.dto;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.hr.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = ApiConstants.NAME)
@Tag(name = "RPC service - TestApi")
public interface TestApi {
String PREFIX=ApiConstants.PREFIX+"/test";
@GetMapping(PREFIX+"/get")
@Operation(summary = "test api")
@Parameter(name = "id",description = "id",required=true,example = "1024")
CommonResult<TestRespDto>getTest(@RequestParam("id")Long id);
}
