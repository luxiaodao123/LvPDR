package com.example.lvpdr.ui.chart;

import static android.hardware.Sensor.TYPE_ROTATION_VECTOR;

import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.lvpdr.R;
import com.example.lvpdr.databinding.FragmentChartBinding;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.crypto.Cipher;

public  class ChartFragment extends Fragment {

    public static final int MAX_VALUES = 400;

    private FragmentChartBinding binding;
    private LineChart chartView;
    private LineDataSet dataSet;
    private LineDataSet dataSet_filter;
    private List<Entry> entries;
    private List<Entry> entries_filter;

    private static ChartFragment singleton = null;

    private int i = 2;

    private LineData lineData;

    public String lineColor = "#FF6655";

    public String lineColor2 = "#FF9593";

    public boolean init = false;

    public float zeroLine = 0.0f;
    public int minValue = 30;
    public int maxValue = 45;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        ChartViewModel chartViewModel =
                new ViewModelProvider(this).get(ChartViewModel.class);
//
//        binding = FragmentChartBinding.inflate(R.layout.fragment_chart, container, false);
//        View root = binding.getRoot();
//
//        final TextView textView = binding.textChart;
//        chartViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);
//        return root;
        if(singleton == null)
            singleton = this;
        return inflater.inflate(R.layout.fragment_chart, container, false);
    }

    public static ChartFragment getInstance(){
        if(singleton == null)
            return null;
        return singleton;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        View view = getView();
        chartView = view.findViewById(R.id.chart);
        _initData();
        _setAxis();
        _setLegend();
        chartView.setEnabled(false);
    }


    private void _setAxis() {
        XAxis x1 = chartView.getXAxis();

        x1.setPosition(XAxis.XAxisPosition.BOTTOM);
        x1.setDrawAxisLine(false);
//        x1.setGranularity(1);
        x1.setDrawGridLines(false);


        YAxis y1 = chartView.getAxisLeft();
        y1.setDrawGridLines(false);
        y1.setAxisMinimum(10);
//        y1.setGranularity(0.5f);
        y1.setDrawZeroLine(false);

        y1.setAxisMaximum(65);
        y1.setLabelCount(20, false);

//        YAxis y1_right = chartView.getAxisRight();
//
//        y1_right.setEnabled(false);
    }

    private void _setLegend() {
        Legend legend = chartView.getLegend();

        legend.setDrawInside(true);

        legend.setOrientation(Legend.LegendOrientation.VERTICAL);

        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.LEFT);

        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);

//        legend.setFormSize(15);

//        legend.setForm(Legend.LegendForm.CIRCLE);

        legend.setTextSize(15);

        legend.setTextColor(Color.BLACK);
    }

    private void _initData() {
        entries = new ArrayList<>();
        entries_filter = new ArrayList<>();
        for (int i = 0; i < MAX_VALUES; i++) {
            entries.add(new Entry(i, zeroLine));
            entries_filter.add(new Entry(i, zeroLine));
        }

        setDataStyle();
    }

    public void setDataStyle(){
        dataSet = new LineDataSet(entries, "");
        dataSet_filter =  new LineDataSet(entries_filter, "");

        //dataSet.setFillColor(Color.parseColor(lineColor));
        dataSet.setColor(Color.parseColor(lineColor));
        dataSet_filter.setColor(Color.parseColor(lineColor));
//
//        dataSet.setDrawFilled(lineColor2!=null);
//        if(lineColor2!=null){
//            dataSet.setColor(Color.parseColor(lineColor2));
//        }

        dataSet.setLineWidth(2);
        dataSet_filter.setLineWidth(2);


//        dataSet.setDrawCircleHole(false);

//        dataSet.setCircleRadius(1);

//        dataSet.setCircleColor(Color.parseColor("#78C256"));

        dataSet.setValueTextSize(13);
        dataSet_filter.setValueTextSize(13);

        dataSet.setValueTextColor(Color.parseColor("#78C256"));
        dataSet_filter.setValueTextColor(Color.parseColor("#000080"));

        dataSet.setMode(LineDataSet.Mode.LINEAR);
        dataSet_filter.setMode(LineDataSet.Mode.LINEAR);

        dataSet.setDrawValues(false);
        dataSet_filter.setDrawValues(false);

        dataSet.setDrawCircles(false);
        dataSet_filter.setDrawCircles(false);

        lineData = new LineData(dataSet);
        lineData.addDataSet(dataSet_filter);

        chartView.setData(lineData);


        init = true;
    }

    public void onUdata(float v, float vf) {
        if(!init){
            return;
        }
        if (i > MAX_VALUES - 1){
            for(int j = 0; j< MAX_VALUES - 1; j++){
                entries.set(j, new Entry(j, entries.get(j+1).getY()));
                entries_filter.set(j, new Entry(j, entries_filter.get(j+1).getY()));
            }
            entries.set(MAX_VALUES - 1, new Entry(MAX_VALUES - 1, v));
            entries_filter.set(MAX_VALUES - 1, new Entry(MAX_VALUES - 1, vf));
        }else{
            entries.set(i, new Entry(i, v));
            entries_filter.set(i, new Entry(i, vf));
            i++;
        }
        chartView.setData(lineData);
        chartView.invalidate();
    }
}