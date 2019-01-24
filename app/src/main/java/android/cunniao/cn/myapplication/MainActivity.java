package android.cunniao.cn.myapplication;

import android.cunniao.cn.myapplication.databinding.ActivityMainBinding;
import android.databinding.DataBindingUtil;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityMainBinding binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
//        setContentView(R.layout.activity_main);

        UserInfo userInfo = new UserInfo();
        userInfo.setAge(123);
        userInfo.setName("张三");
        binding.setUser(userInfo);
    }
}
