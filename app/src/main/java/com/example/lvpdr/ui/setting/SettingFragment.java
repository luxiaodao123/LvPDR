package com.example.lvpdr.ui.setting;

import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.provider.Settings;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.example.lvpdr.R;
import com.example.lvpdr.Sender;
import com.example.lvpdr.data.cache.RedisCache;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.ArrayList;
import java.util.Arrays;

public class SettingFragment extends Fragment {
    private static final String TAG = "SettingFragment";
    private static FragmentActivity mActivity;
    private SettingViewModel mViewModel;
    private String androidId = "";
    Sender mSender;

    public static SettingFragment newInstance() {
        return new SettingFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_setting, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mViewModel = new ViewModelProvider(this).get(SettingViewModel.class);
        //
        androidId = Settings.Secure.getString(getContext().getContentResolver(), Settings.Secure.ANDROID_ID);
        _initWidget();

        View view = getView();
        mActivity = getActivity();

        Button button = (Button) view.findViewById(R.id.modeSwtButton);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
                View nView = getLayoutInflater().inflate(R.layout.dialog_spinner, null);
                builder.setTitle("请选择车辆");

                // Set up the spinner
                Spinner spinner = (Spinner) nView.findViewById(R.id.spinner);
                String[] options = {"选项1", "选项2", "选项3"};
                ArrayAdapter<String> adapter = null;
                adapter = new ArrayAdapter<String>(mActivity, androidx.appcompat.R.layout.support_simple_spinner_dropdown_item, options);
                spinner.setAdapter(adapter);
                builder.setView(spinner);

                // Set up the buttons
                builder.setPositiveButton("确认", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
//                        input.getText().toString();
                    }
                });
                builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
                builder.show();
            }
        });
    }

    private void _initWidget() {
        //显示设备编号
        TextView text = (TextView) getView().findViewById(R.id.deviceStrVal);
        text.setText(androidId);

        /**
         * 显示员工姓名
         * 先查询该设备有没有绑定过
         */

        /**
         * 显示车辆名称
         */

    }

}