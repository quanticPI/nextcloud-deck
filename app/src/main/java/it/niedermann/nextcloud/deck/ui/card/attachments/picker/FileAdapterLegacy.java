package it.niedermann.nextcloud.deck.ui.card.attachments.picker;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import it.niedermann.nextcloud.deck.databinding.ItemAttachmentDefaultBinding;
import it.niedermann.nextcloud.deck.databinding.ItemPickerNativeBinding;
import it.niedermann.nextcloud.deck.util.AttachmentUtil;

import static java.util.Collections.reverseOrder;
import static java.util.Comparator.comparingLong;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

@Deprecated
public class FileAdapterLegacy extends AbstractPickerAdapter<RecyclerView.ViewHolder> {

    @NonNull
    private final Context context;
    @NonNull
    private final List<File> files;
    @NonNull
    protected final Consumer<Uri> onSelect;
    @NonNull
    protected final Runnable openNativePicker;

    public FileAdapterLegacy(@NonNull Context context, @NonNull Consumer<Uri> onSelect, @NonNull Runnable openNativePicker) {
        // TODO run in separate thread?
        this.context = context;
        this.onSelect = onSelect;
        this.openNativePicker = openNativePicker;
        this.files = Arrays.stream(requireNonNull(requireNonNull(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)).listFiles()))
                .sorted(reverseOrder(comparingLong(File::lastModified)))
                .collect(toList());

    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        switch (viewType) {
            case VIEW_TYPE_ITEM_NATIVE:
                return new FileNativeItemViewHolder(ItemPickerNativeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
            case VIEW_TYPE_ITEM:
                return new FileItemViewHolder(ItemAttachmentDefaultBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
            default:
                throw new IllegalStateException("Unknown viewType " + viewType);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        switch (getItemViewType(position)) {
            case VIEW_TYPE_ITEM_NATIVE: {
                ((FileNativeItemViewHolder) holder).bind(openNativePicker);
                break;
            }
            case VIEW_TYPE_ITEM: {
                final File file = files.get(position - 1);
                if (file.isFile()) {
                    ((FileItemViewHolder) holder).bind(Uri.fromFile(file), file.getName(), AttachmentUtil.getMimeType(file.getAbsolutePath()), file.length(), file.lastModified(), onSelect);
                } else {
                    ((FileItemViewHolder) holder).bindError();
                }
                break;
            }
        }
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    public void onDestroy() {
        // Let GarbageCollection do this stuff...
    }
}