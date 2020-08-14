import com.alibaba.druid.pool.DruidDataSource;
import org.sagacity.sqltoy.SqlToyContext;

import javax.sql.DataSource;

public class Test {
    public static void main(String[] args) {
        SqlToyContext sqlToyContext = new SqlToyContext();
        sqlToyContext.setBeanManagerName("sqltoy");
        sqlToyContext.setConnectionManagerName("sqltoy");
        sqlToyContext.setSqlResourcesDir("classpath:/sqltoy/");
        sqlToyContext.setTranslateConfig("classpath:sqltoy-translate.xml");
        sqlToyContext.setDebug(true);

        DruidDataSource dataSource = new DruidDataSource();
        dataSource.setUrl("jdbc:postgresql://localhost:5432/uoe");
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUsername("postgres");
        dataSource.setPassword("4CWfBhjYJEfzNfrV");

        sqlToyContext.setDefaultDataSource(dataSource);
        try {
            sqlToyContext.initialize();


            TtService ttService = new TtService();
            ttService.sayHello("ddd");
        } catch (Exception e) {
            e.printStackTrace();
        }


    }
}
