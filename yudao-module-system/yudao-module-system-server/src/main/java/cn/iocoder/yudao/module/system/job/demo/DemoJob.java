package cn.iocoder.yudao.module.system.job.demo;

import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.stereotype.Component;

@Component("systemDemoJob")
public class DemoJob {

    @XxlJob("systemDemoJob")
    @TenantJob
    public void execute() {
        System.out.println("美滋滋");
    }

}
