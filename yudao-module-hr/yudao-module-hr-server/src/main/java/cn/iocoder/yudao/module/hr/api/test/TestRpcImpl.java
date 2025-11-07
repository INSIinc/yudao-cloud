package cn.iocoder.yudao.module.hr.api.test;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.hr.api.test.dto.TestApi;
import cn.iocoder.yudao.module.hr.api.test.dto.TestRespDto;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
public class TestRpcImpl implements TestApi {

    @Override
    public CommonResult<TestRespDto> getTest(Long id) {
        return null;
    }
}
