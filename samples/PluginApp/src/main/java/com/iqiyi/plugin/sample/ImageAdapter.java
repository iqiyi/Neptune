package com.iqiyi.plugin.sample;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.facebook.drawee.view.SimpleDraweeView;

import java.util.List;

public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ImageViewHolder> {

    private final Context mContext;
    private List<String> mUrls;

    public ImageAdapter(Context context, List<String> urls){
        mContext = context;
        mUrls = urls;
    }

    @Override
    public ImageViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        View rootView = LayoutInflater.from(mContext).inflate(R.layout.activity_fresco_item, parent, false);
        rootView.setClickable(true);
        return new ImageViewHolder(rootView);
    }

    @Override
    public void onBindViewHolder(final ImageViewHolder holder, int position) {

        String url = mUrls.get(position);
        holder.mImage.setImageURI(url);
    }



    @Override
    public int getItemCount() {
        return mUrls.size();
    }


    static class ImageViewHolder extends RecyclerView.ViewHolder{

        private TextView mTitle;
        private SimpleDraweeView mImage;

        public ImageViewHolder(View itemView) {
            super(itemView);
            mTitle = (TextView) itemView.findViewById(R.id.image_title);
            mImage = (SimpleDraweeView) itemView.findViewById(R.id.image_content);
        }
    }
}
