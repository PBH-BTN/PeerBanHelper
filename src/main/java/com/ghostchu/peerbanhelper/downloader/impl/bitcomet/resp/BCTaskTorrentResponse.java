package com.ghostchu.peerbanhelper.downloader.impl.bitcomet.resp;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@Data
public class BCTaskTorrentResponse {

    @SerializedName("error_code")
    private String errorCode;
    @SerializedName("task_detail")
    private TaskDetailDTO taskDetail;
    @SerializedName("task_status")
    private TaskStatusDTO taskStatus;
    @SerializedName("task_summary")
    private TaskSummaryDTO taskSummary;
    @SerializedName("task")
    private TaskDTO task;
//    @SerializedName("version")
//    private String version;

    @NoArgsConstructor
    @Data
    public static class TaskDetailDTO {
        @SerializedName("type")
        private String type;
        @SerializedName("infohash")
        private String infohash;
        @SerializedName("infohash_v2")
        private String infohashV2;
        @SerializedName("download_link")
        private String downloadLink;
        @SerializedName("task_name")
        private String taskName;
//        @SerializedName("save_folder")
//        private String saveFolder;
        @SerializedName("total_size")
        private long totalSize;
//        @SerializedName("file_num")
//        private String fileNum;
//        @SerializedName("added_time")
//        private String addedTime;
//        @SerializedName("finish_time")
//        private String finishTime;
//        @SerializedName("created_by")
//        private String createdBy;
//        @SerializedName("creation_time")
//        private String creationTime;
//        @SerializedName("description")
//        private String description;
//        @SerializedName("publisher")
//        private String publisher;
//        @SerializedName("publisher_url")
//        private String publisherUrl;
        @SerializedName("torrent_private")
        private Boolean torrentPrivate;
    }

    @NoArgsConstructor
    @Data
    public static class TaskStatusDTO {
        @SerializedName("status")
        private String status;
//        @SerializedName("dl_speed")
//        private String dlSpeed;
//        @SerializedName("up_speed")
//        private String upSpeed;
        @SerializedName("dl_size")
        private long dlSize;
        @SerializedName("up_size")
        private long upSize;
        @SerializedName("total_size")
        private long totalSize;
//        @SerializedName("progress")
//        private String progress;
//        @SerializedName("share_ratio")
//        private String shareRatio;
//        @SerializedName("left_time")
//        private String leftTime;
//        @SerializedName("peers_num")
//        private String peersNum;
//        @SerializedName("seeders_num")
//        private String seedersNum;
//        @SerializedName("active_time")
//        private String activeTime;
//        @SerializedName("seeding_time")
//        private String seedingTime;
//        @SerializedName("availability")
//        private String availability;
//        @SerializedName("piece_num")
//        private String pieceNum;
//        @SerializedName("ltseed_upload_size")
//        private Long ltseedUploadSize;
//        @SerializedName("ltseed_upload_speed")
//        private String ltseedUploadSpeed;
//        @SerializedName("size_left")
//        private Long sizeLeft;
        @SerializedName("download_permillage")
        private Long downloadPermillage;
//        @SerializedName("dl_time")
//        private String dlTime;
//        @SerializedName("dl_time_all")
//        private String dlTimeAll;
//        @SerializedName("files_selected")
//        private Long filesSelected;
//        @SerializedName("piece_hashes")
//        private String pieceHashes;
//        @SerializedName("file_alignment")
//        private String fileAlignment;
//        @SerializedName("ltseed_share_ratio")
//        private String ltseedShareRatio;
    }

    @NoArgsConstructor
    @Data
    public static class TaskSummaryDTO {
        @SerializedName("tags")
        private String tags;
//        @SerializedName("speed_list")
//        private List<Long> speedList;
//        @SerializedName("downloaded_pieces")
//        private List<Boolean> downloadedPieces;
//        @SerializedName("available_pieces")
//        private List<Integer> availablePieces;
    }

    @NoArgsConstructor
    @Data
    public static class TaskDTO {
        @SerializedName("task_id")
        private long taskId;
//        @SerializedName("task_guid")
//        private String taskGuid;
        @SerializedName("type")
        private String type;
        @SerializedName("task_name")
        private String taskName;
        @SerializedName("status")
        private String status;
        @SerializedName("total_size")
        private long totalSize;
//        @SerializedName("selected_size")
//        private long selectedSize;
//        @SerializedName("selected_downloaded_size")
//        private long selectedDownloadedSize;
        @SerializedName("download_rate")
        private long downloadRate;
        @SerializedName("upload_rate")
        private long uploadRate;
//        @SerializedName("error_code")
//        private String errorCode;
//        @SerializedName("error_message")
//        private String errorMessage;
        @SerializedName("permillage")
        private long permillage;
//        @SerializedName("left_time")
//        private String leftTime;
    }
}
