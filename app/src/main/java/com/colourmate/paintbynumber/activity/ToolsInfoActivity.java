package com.colourmate.paintbynumber.activity;

import android.graphics.Color;
import android.os.Bundle;
import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.Utils.CompatUtils;
import com.colourmate.paintbynumber.databinding.ActivityToolsInfoBinding;

public class ToolsInfoActivity extends BaseActivity {

    private ActivityToolsInfoBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        overridePendingTransition(R.anim.slide_from_right, R.anim.slide_to_left);
        
        binding = ActivityToolsInfoBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Paper ground + transparent status bar; the toolbar takes the inset (design pass 2026-10-14).
        com.colourmate.paintbynumber.util.EdgeToEdge.apply(this, binding.toolbar, null);
        
        binding.rlBack.setOnClickListener(v -> finish());
    }
}
