package it.niedermann.nextcloud.deck.ui.card.attachments;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityOptionsCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

import it.niedermann.nextcloud.deck.R;
import it.niedermann.nextcloud.deck.databinding.ItemAttachmentDefaultBinding;
import it.niedermann.nextcloud.deck.databinding.ItemAttachmentImageBinding;
import it.niedermann.nextcloud.deck.model.Account;
import it.niedermann.nextcloud.deck.model.Attachment;
import it.niedermann.nextcloud.deck.model.enums.DBStatus;
import it.niedermann.nextcloud.deck.ui.AttachmentsActivity;
import it.niedermann.nextcloud.deck.util.AttachmentUtil;
import it.niedermann.nextcloud.deck.util.DateUtil;
import it.niedermann.nextcloud.deck.util.DeleteDialogBuilder;

import static it.niedermann.nextcloud.deck.ui.AttachmentsActivity.BUNDLE_KEY_CURRENT_ATTACHMENT_LOCAL_ID;
import static it.niedermann.nextcloud.deck.ui.card.CardAdapter.BUNDLE_KEY_ACCOUNT_ID;
import static it.niedermann.nextcloud.deck.ui.card.CardAdapter.BUNDLE_KEY_LOCAL_ID;
import static it.niedermann.nextcloud.deck.ui.card.CardAdapter.NO_LOCAL_ID;
import static it.niedermann.nextcloud.deck.util.ClipboardUtil.copyToClipboard;

@SuppressWarnings("WeakerAccess")
public class CardAttachmentAdapter extends RecyclerView.Adapter<AttachmentViewHolder> {

    public static final int VIEW_TYPE_DEFAULT = 2;
    public static final int VIEW_TYPE_IMAGE = 1;

    private final MenuInflater menuInflater;
    private final Account account;
    @Nullable
    private Long cardRemoteId = null;
    private final long cardLocalId;
    @NonNull
    private List<Attachment> attachments = new ArrayList<>();
    @NonNull
    private final AttachmentDeletedListener attachmentDeletedListener;
    @Nullable
    private final AttachmentClickedListener attachmentClickedListener;

    CardAttachmentAdapter(
            @NonNull MenuInflater menuInflater,
            @NonNull AttachmentDeletedListener attachmentDeletedListener,
            @Nullable AttachmentClickedListener attachmentClickedListener,
            @NonNull Account account,
            long cardLocalId
    ) {
        super();
        this.menuInflater = menuInflater;
        this.attachmentDeletedListener = attachmentDeletedListener;
        this.attachmentClickedListener = attachmentClickedListener;
        this.account = account;
        this.cardLocalId = cardLocalId;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        Long id = attachments.get(position).getLocalId();
        return id == null ? NO_LOCAL_ID : id;
    }

    @NonNull
    @Override
    public AttachmentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final Context context = parent.getContext();
        switch (viewType) {
            case VIEW_TYPE_IMAGE:
                return new ImageAttachmentViewHolder(ItemAttachmentImageBinding.inflate(LayoutInflater.from(context), parent, false));
            case VIEW_TYPE_DEFAULT:
            default:
                return new DefaultAttachmentViewHolder(ItemAttachmentDefaultBinding.inflate(LayoutInflater.from(context), parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull AttachmentViewHolder holder, int position) {
        final Context context = holder.itemView.getContext();
        final Attachment attachment = attachments.get(position);
        final int viewType = getItemViewType(position);

        @Nullable final String uri = (attachment.getId() == null || cardRemoteId == null)
                ? null :
                AttachmentUtil.getUrl(account.getUrl(), cardRemoteId, attachment.getId());
        holder.setNotSyncedYetStatus(!DBStatus.LOCAL_EDITED.equals(attachment.getStatusEnum()));
        holder.itemView.setOnCreateContextMenuListener((menu, v, menuInfo) -> {
            menuInflater.inflate(R.menu.attachment_menu, menu);
            menu.findItem(R.id.delete).setOnMenuItemClickListener(item -> {
                new DeleteDialogBuilder(context)
                        .setTitle(context.getString(R.string.delete_something, attachment.getFilename()))
                        .setMessage(R.string.attachment_delete_message)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.simple_delete, (dialog, which) -> attachmentDeletedListener.onAttachmentDeleted(attachment))
                        .show();
                return false;
            });
            if (uri == null) {
                menu.findItem(android.R.id.copyUrl).setVisible(false);
            } else {
                menu.findItem(android.R.id.copyUrl).setOnMenuItemClickListener(item -> copyToClipboard(context, attachment.getFilename(), uri));
            }
        });

        switch (viewType) {
            case VIEW_TYPE_IMAGE: {
                holder.getPreview().setImageResource(R.drawable.ic_image_grey600_24dp);
                Glide.with(context)
                        .load(uri)
                        .error(R.drawable.ic_image_grey600_24dp)
                        .into(holder.getPreview());
                holder.itemView.setOnClickListener((v) -> {
                    if (attachmentClickedListener != null) {
                        attachmentClickedListener.onAttachmentClicked(position);
                    }
                    Intent intent = new Intent(context, AttachmentsActivity.class);
                    intent.putExtra(BUNDLE_KEY_ACCOUNT_ID, account.getId());
                    intent.putExtra(BUNDLE_KEY_LOCAL_ID, cardLocalId);
                    intent.putExtra(BUNDLE_KEY_CURRENT_ATTACHMENT_LOCAL_ID, attachment.getLocalId());
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && context instanceof Activity) {
                        String transitionName = context.getString(R.string.transition_attachment_preview, String.valueOf(attachment.getLocalId()));
                        holder.getPreview().setTransitionName(transitionName);
                        context.startActivity(intent, ActivityOptionsCompat.makeSceneTransitionAnimation((Activity) context, holder.getPreview(), transitionName).toBundle());
                    } else {
                        context.startActivity(intent);
                    }
                });
                break;
            }
            case VIEW_TYPE_DEFAULT:
            default: {
                DefaultAttachmentViewHolder defaultHolder = (DefaultAttachmentViewHolder) holder;

                if (attachment.getMimetype() != null) {
                    if (attachment.getMimetype().startsWith("audio")) {
                        holder.getPreview().setImageResource(R.drawable.ic_music_note_grey600_24dp);
                    } else if (attachment.getMimetype().startsWith("video")) {
                        holder.getPreview().setImageResource(R.drawable.ic_local_movies_grey600_24dp);
                    }
                }

                if (cardRemoteId != null) {
                    defaultHolder.itemView.setOnClickListener((event) -> {
                        Intent openURL = new Intent(Intent.ACTION_VIEW);
                        openURL.setData(Uri.parse(AttachmentUtil.getUrl(account.getUrl(), cardRemoteId, attachment.getId())));
                        context.startActivity(openURL);
                    });
                }
                defaultHolder.binding.filename.setText(attachment.getBasename());
                defaultHolder.binding.filesize.setText(Formatter.formatFileSize(context, attachment.getFilesize()));
                if (attachment.getLastModifiedLocal() != null) {
                    defaultHolder.binding.modified.setText(DateUtil.getRelativeDateTimeString(context, attachment.getLastModifiedLocal().getTime()));
                    defaultHolder.binding.modified.setVisibility(View.VISIBLE);
                } else if (attachment.getLastModified() != null) {
                    defaultHolder.binding.modified.setText(DateUtil.getRelativeDateTimeString(context, attachment.getLastModified().getTime()));
                    defaultHolder.binding.modified.setVisibility(View.VISIBLE);
                } else {
                    defaultHolder.binding.modified.setVisibility(View.GONE);
                }
                break;
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        String mimeType = attachments.get(position).getMimetype();
        return (mimeType != null && mimeType.startsWith("image")) ? VIEW_TYPE_IMAGE : VIEW_TYPE_DEFAULT;
    }

    @Override
    public int getItemCount() {
        return attachments.size();
    }

    public void setAttachments(@NonNull List<Attachment> attachments, long cardRemoteId) {
        this.cardRemoteId = cardRemoteId;
        this.attachments = attachments;
        notifyDataSetChanged();
    }

    public void addAttachment(Attachment a) {
        this.attachments.add(a);
        notifyItemInserted(this.attachments.size());
    }

    public void removeAttachment(Attachment a) {
        for (int i = 0; i < this.attachments.size(); i++) {
            if (this.attachments.get(i).equals(a)) {
                this.attachments.remove(i);
                notifyItemRemoved(i);
                return;
            }
        }
    }
}