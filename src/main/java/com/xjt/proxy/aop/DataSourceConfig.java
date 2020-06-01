package com.xjt.proxy.aop;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceBuilder;
import com.xjt.proxy.dynamicdatasource.DynamicDataSource;
import com.xjt.proxy.dynamicdatasource.DynamicDataSourceEnum;

import tk.mybatis.spring.annotation.MapperScan;

/**
 * 主从配置
 * 
 * @author kevin
 * @date 2019-11-20 9:49
 * 这里，我们配置了4个数据源，1个master，2两个slave，1个路由数据源。
 * 前3个数据源都是为了生成第4个数据源，而且后续我们只用这最后一个路由数据源。
 */
@Configuration
@MapperScan(basePackages = "com.xjt.proxy.mapper", sqlSessionTemplateRef = "sqlTemplate")
public class DataSourceConfig {
    /**
     * 主库
     */
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.master")
    public DataSource masterDb() {
        return DruidDataSourceBuilder.create().build();
    }

    /**
     * 从库
     */
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.slave")
    public DataSource slaveDb() {
        return DruidDataSourceBuilder.create().build();
    }



//    /**
//     * 从库2
//     */
//    @Bean
//    @ConfigurationProperties(prefix = "spring.datasource.slave1")
//    public DataSource slaveDb2() {
//        return DruidDataSourceBuilder.create().build();
//    }

    /**
     * 主从动态配置        Spring 允许我们通过 @Qualifier 注释指定注入 Bean 的名称，这样歧义就消除了。
     *  @Qualifier        注解 主要用来明确表示注入的是那个类  区别一个类有多个实现的时候。
     *  @Qualifier("XXX") 中的 XX是Bean 的名称
     *  该类的作用是将多个数据源加入到池子中  并设置默认的数据源
     *  有几个数据源在该方法中就有几个参数   然后添加到 继承自AbstractRoutingDataSource的自定义的类中的map中
     *   dynamicDb(@Qualifier("masterDb") DataSource masterDataSource,
     *          @Qualifier("slaveDb") DataSource slaveDataSource1，@Qualifier("slaveDb2") DataSource slaveDataSource2，
     *          @Qualifier("slaveDb3") DataSource slaveDataSource3)
     */
    @Bean
    public DynamicDataSource dynamicDb(@Qualifier("masterDb") DataSource masterDataSource,
        @Autowired(required = false) @Qualifier("slaveDb") DataSource slaveDataSource) {
        DynamicDataSource dynamicDataSource = new DynamicDataSource();
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(DynamicDataSourceEnum.MASTER.getDataSourceName(), masterDataSource);
        if (slaveDataSource != null) {
            targetDataSources.put(DynamicDataSourceEnum.SLAVE.getDataSourceName(), slaveDataSource);
        }
        dynamicDataSource.setTargetDataSources(targetDataSources);
        dynamicDataSource.setDefaultTargetDataSource(masterDataSource);
        return dynamicDataSource;
    }

    //MyBatis配置  将mybatis的数据源设置成上面的动态数据源
    @Bean
    public SqlSessionFactory sessionFactory(@Qualifier("dynamicDb") DataSource dynamicDataSource) throws Exception {
        SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
        bean.setMapperLocations(
            new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/*Mapper.xml")
        );
        bean.setDataSource(dynamicDataSource);
        return bean.getObject();
    }

    //设置sqlTemplate的模板   SqlSessionTemplate 相当于会话   类似于我们在网站中打开的网页 相当于一个session
    @Bean
    public SqlSessionTemplate sqlTemplate(@Qualifier("sessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    //为事务设置数据源
    @Bean(name = "dataSourceTx")
    public DataSourceTransactionManager dataSourceTx(@Qualifier("dynamicDb") DataSource dynamicDataSource) {
        DataSourceTransactionManager dataSourceTransactionManager = new DataSourceTransactionManager();
        dataSourceTransactionManager.setDataSource(dynamicDataSource);
        return dataSourceTransactionManager;
    }
}
