package cn.iocoder.yudao.module.hr.job.demo;

import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.stereotype.Component;

@Component("hrDemoJob")
public class DemoJob {

    @XxlJob("hrDemoJob")
    @TenantJob
    public void execute() {
        System.out.println("美滋滋");
    }

}
