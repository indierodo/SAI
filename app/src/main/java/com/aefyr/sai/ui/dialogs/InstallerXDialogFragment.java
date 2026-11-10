package com.aefyr.sai.ui.dialogs;

import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aefyr.sai.R;
import com.aefyr.sai.adapters.SplitApkSourceMetaAdapter;
import com.aefyr.sai.installerx.resolver.urimess.UriHostFactory;
import com.aefyr.sai.ui.dialogs.base.BaseBottomSheetDialogFragment;
import com.aefyr.sai.view.ViewSwitcherLayout;
import com.aefyr.sai.viewmodels.InstallerXDialogViewModel;
import com.aefyr.sai.viewmodels.factory.InstallerXDialogViewModelFactory;
import java.util.ArrayList;
import java.util.List;

public class InstallerXDialogFragment extends BaseBottomSheetDialogFragment {
    private static final String ARG_APK_SOURCE_URIS = "apk_source_uris";
    private static final String ARG_URI_HOST_FACTORY = "uri_host_factory";

    private InstallerXDialogViewModel mViewModel;

    /**
     * Create an instance of InstallerXDialogFragment with given apk source uri and UriHostFactory class.
     * If {@code uriHostFactoryClass} is null, {@link com.aefyr.sai.installerx.resolver.urimess.impl.AndroidUriHost} will be used.
     *
     * @param apkSourceUri
     * @param uriHostFactoryClass
     * @return
     */
    public static InstallerXDialogFragment newInstance(List<Uri> apkSourceUris, @Nullable Class<? extends UriHostFactory> uriHostFactoryClass) {
        Bundle args = new Bundle();
        args.putParcelableArrayList(ARG_APK_SOURCE_URIS, new ArrayList<>(apkSourceUris));

        if (uriHostFactoryClass != null)
            args.putString(ARG_URI_HOST_FACTORY, uriHostFactoryClass.getCanonicalName());

        InstallerXDialogFragment fragment = new InstallerXDialogFragment();
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();

        UriHostFactory uriHostFactory = null;
        if (args != null) {
            String uriHostFactoryClass = args.getString(ARG_URI_HOST_FACTORY);
            if (uriHostFactoryClass != null) {
                try {
                    uriHostFactory = (UriHostFactory) Class.forName(uriHostFactoryClass).getConstructor().newInstance();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        mViewModel = new ViewModelProvider(this, new InstallerXDialogViewModelFactory(requireContext(), uriHostFactory)).get(InstallerXDialogViewModel.class);

        if (args == null)
            return;

        ArrayList<Uri> apkSourceUris = args.getParcelableArrayList(ARG_APK_SOURCE_URIS);
        if (apkSourceUris != null && !apkSourceUris.isEmpty())
            mViewModel.setApkSourceUris(apkSourceUris);
    }

    @Nullable
    @Override
    protected View onCreateContentView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_installerx, container, false);
    }

    @Override
    protected void onContentViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onContentViewCreated(view, savedInstanceState);

        setTitle(R.string.installerx_dialog_title);
        getPositiveButton().setText(R.string.installerx_dialog_install);

        ViewSwitcherLayout viewSwitcher = view.findViewById(R.id.container_dialog_installerx);

        RecyclerView recycler = view.findViewById(R.id.rv_dialog_installerx_content);
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        recycler.getRecycledViewPool().setMaxRecycledViews(SplitApkSourceMetaAdapter.VH_TYPE_SPLIT_PART, 16);

        SplitApkSourceMetaAdapter adapter = new SplitApkSourceMetaAdapter(mViewModel.getPartsSelection(), this, requireContext());
        recycler.setAdapter(adapter);

        getNegativeButton().setOnClickListener(v -> dismiss());
        getPositiveButton().setOnClickListener(v -> {
            mViewModel.enqueueInstallation();
            dismiss();
        });

        TextView warningTv = view.findViewById(R.id.tv_installerx_warning);
        mViewModel.getState().observe(this, state -> {
            switch (state) {
                case NO_DATA:
                    getPositiveButton().setVisibility(View.GONE);
                    break;
                case LOADING:
                    viewSwitcher.setShownView(R.id.container_installerx_loading);
                    getPositiveButton().setVisibility(View.GONE);
                    break;
                case LOADED:
                    viewSwitcher.setShownView(R.id.rv_dialog_installerx_content);
                    getPositiveButton().setVisibility(View.VISIBLE);
                    break;
                case WARNING:
                    viewSwitcher.setShownView(R.id.container_installerx_warning);
                    warningTv.setText(mViewModel.getWarning().message());
                    getPositiveButton().setVisibility(mViewModel.getWarning().canInstallAnyway() ? View.VISIBLE : View.GONE);
                    break;
                case ERROR:
                    viewSwitcher.setShownView(R.id.container_installerx_error);
                    getPositiveButton().setVisibility(View.VISIBLE);
                    break;
            }
            revealBottomSheet();
        });

        mViewModel.getMeta().observe(this, meta -> {
            adapter.setMeta(meta);
            revealBottomSheet();
        });

        view.requestFocus(); //TV fix
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);

        if (mViewModel.getState().getValue() == InstallerXDialogViewModel.State.LOADING)
            mViewModel.cancelParsing();
    }

}
