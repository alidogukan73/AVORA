package com.alidogukan.avora.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.alidogukan.avora.R;
import com.alidogukan.avora.feedback.FeedbackInboxRepository.Message;
import com.google.android.material.button.MaterialButton;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public final class FeedbackInboxAdapter extends RecyclerView.Adapter<FeedbackInboxAdapter.Holder> {
    public interface Listener {
        void onOpen(Message message);
        void onDelete(Message message);
    }

    private final Listener listener;
    private final List<Message> items = new ArrayList<>();

    public FeedbackInboxAdapter(Listener listener) { this.listener = listener; }

    public void submit(List<Message> values) {
        items.clear();
        if (values != null) items.addAll(values);
        notifyDataSetChanged();
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_feedback_inbox, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override public int getItemCount() { return items.size(); }

    final class Holder extends RecyclerView.ViewHolder {
        private final TextView title = itemView.findViewById(R.id.txtFeedbackInboxTitle);
        private final TextView meta = itemView.findViewById(R.id.txtFeedbackInboxMeta);
        private final TextView preview = itemView.findViewById(R.id.txtFeedbackInboxPreview);
        private final TextView status = itemView.findViewById(R.id.txtFeedbackInboxStatus);
        private final MaterialButton open = itemView.findViewById(R.id.btnFeedbackInboxOpen);
        private final MaterialButton delete = itemView.findViewById(R.id.btnFeedbackInboxDelete);

        Holder(@NonNull View itemView) { super(itemView); }

        void bind(Message message) {
            title.setText(message.subject.isBlank() ? itemView.getContext()
                    .getString(R.string.feedback_inbox_no_subject) : message.subject);
            String date = message.createdAt <= 0 ? "—" : DateFormat
                    .getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(new Date(message.createdAt * 1000L));
            meta.setText(message.area + " · " + date);
            preview.setText(message.description);
            status.setText(statusLabel(message.status));
            itemView.setOnClickListener(view -> listener.onOpen(message));
            open.setOnClickListener(view -> listener.onOpen(message));
            delete.setOnClickListener(view -> listener.onDelete(message));
        }

        private int statusLabel(String value) {
            if ("read".equals(value)) return R.string.feedback_inbox_status_read;
            if ("resolved".equals(value)) return R.string.feedback_inbox_status_resolved;
            return R.string.feedback_inbox_status_new;
        }
    }
}
