package com.iqiyi.plugin.sample;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;

import com.facebook.common.logging.FLog;
import com.facebook.drawee.backends.pipeline.Fresco;



import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity{
    private static final String TAG = "MainActivity";

    private static final String[] URL_LINKS = {
            "http://desk.fd.zol-img.com.cn/t_s1440x900c5/g5/M00/0F/09/ChMkJlauzbOIb6JqABF4o12gc_AAAH9HgF1sh0AEXi7441.jpg",
            "http://desk.fd.zol-img.com.cn/t_s1440x900c5/g5/M00/08/0A/ChMkJli9XIOIHZlxACrBWTH-3-kAAae8QCVIF4AKsFx521.jpg",
            "http://desk.fd.zol-img.com.cn/t_s1440x900c5/g5/M00/08/0A/ChMkJ1i9XJmIJnFtABXosJGWaOkAAae8QGrHE8AFejI057.jpg",
            "http://desk.fd.zol-img.com.cn/t_s1440x900c5/g5/M00/08/0A/ChMkJ1i9XIWIfPrHACa8wnLl-YYAAae8QDvfUkAJrza322.jpg",
            "http://desk.fd.zol-img.com.cn/t_s1440x900c5/g5/M00/08/0A/ChMkJli9XJuIGhrMADspbh_OzE4AAae8QHCouwAOymG127.jpg",
    };

    private RecyclerView mRecyclerView;
    private ImageAdapter mAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Fresco.initialize(this);
        FLog.setMinimumLoggingLevel(FLog.VERBOSE);

        setContentView(R.layout.activity_main);
        Log.i(TAG, "MainActivity onCreate() called");

        mRecyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        List<String> mImageUrls = Arrays.asList(URL_LINKS);
        mAdapter = new ImageAdapter(this, mImageUrls);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.setAdapter(mAdapter);
    }


    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "MainActivity onResume() called");
    }
}
