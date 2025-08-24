package top.asimov.pigeon;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("top.asimov.pigeon.mapper")
public class App {

  public static void main(String[] args) {
    SpringApplication.run(App.class, args);
  }

}
