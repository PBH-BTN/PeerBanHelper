package com.ghostchu.peerbanhelper.downloader.impl.bitcomet.resp;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@Data
public class BCTaskTorrentResponse {

    @JsonProperty("error_code")
    private String errorCode;
    @JsonProperty("task_detail")
    private TaskDetailDTO taskDetail;
    @JsonProperty("task_status")
    private TaskStatusDTO taskStatus;
    @JsonProperty("task_summary")
    private TaskSummaryDTO taskSummary;
    @JsonProperty("task")
    private TaskDTO task;
    @JsonProperty("version")
    private String version;

    @NoArgsConstructor
    @Data
    public static class TaskDetailDTO {
        @JsonProperty("type")
        private String type;
        @JsonProperty("infohash")
        private String infohash;
        @JsonProperty("infohash_v2")
        private String infohashV2;
        @JsonProperty("download_link")
        private String downloadLink;
        @JsonProperty("task_name")
        private String taskName;
        @JsonProperty("save_folder")
        private String saveFolder;
        @JsonProperty("total_size")
        private Long totalSize;
        @JsonProperty("file_num")
        private String fileNum;
        @JsonProperty("added_time")
        private String addedTime;
        @JsonProperty("finish_time")
        private String finishTime;
        @JsonProperty("created_by")
        private String createdBy;
        @JsonProperty("creation_time")
        private String creationTime;
        @JsonProperty("description")
        private String description;
        @JsonProperty("publisher")
        private String publisher;
        @JsonProperty("publisher_url")
        private String publisherUrl;
        @JsonProperty("torrent_private")
        private Boolean torrentPrivate;
    }

    @NoArgsConstructor
    @Data
    public static class TaskStatusDTO {
        @JsonProperty("status")
        private String status;
        @JsonProperty("dl_speed")
        private String dlSpeed;
        @JsonProperty("up_speed")
        private String upSpeed;
        @JsonProperty("dl_size")
        private Integer dlSize;
        @JsonProperty("up_size")
        private Integer upSize;
        @JsonProperty("total_size")
        private Long totalSize;
        @JsonProperty("progress")
        private String progress;
        @JsonProperty("share_ratio")
        private String shareRatio;
        @JsonProperty("left_time")
        private String leftTime;
        @JsonProperty("peers_num")
        private String peersNum;
        @JsonProperty("seeders_num")
        private String seedersNum;
        @JsonProperty("active_time")
        private String activeTime;
        @JsonProperty("seeding_time")
        private String seedingTime;
        @JsonProperty("availability")
        private String availability;
        @JsonProperty("piece_num")
        private String pieceNum;
        @JsonProperty("ltseed_upload_size")
        private Integer ltseedUploadSize;
        @JsonProperty("ltseed_upload_speed")
        private String ltseedUploadSpeed;
        @JsonProperty("size_left")
        private Long sizeLeft;
        @JsonProperty("download_permillage")
        private Integer downloadPermillage;
        @JsonProperty("dl_time")
        private String dlTime;
        @JsonProperty("dl_time_all")
        private String dlTimeAll;
        @JsonProperty("files_selected")
        private Integer filesSelected;
        @JsonProperty("piece_hashes")
        private String pieceHashes;
        @JsonProperty("file_alignment")
        private String fileAlignment;
        @JsonProperty("ltseed_share_ratio")
        private String ltseedShareRatio;
    }

    @NoArgsConstructor
    @Data
    public static class TaskSummaryDTO {
        @JsonProperty("tags")
        private String tags;
        @JsonProperty("speed_list")
        private List<Integer> speedList;
        @JsonProperty("downloaded_pieces")
        private List<Boolean> downloadedPieces;
        @JsonProperty("available_pieces")
        private List<Integer> availablePieces;
    }

    @NoArgsConstructor
    @Data
    public static class TaskDTO {
        @JsonProperty("task_id")
        private Integer taskId;
        @JsonProperty("task_guid")
        private String taskGuid;
        @JsonProperty("type")
        private String type;
        @JsonProperty("task_name")
        private String taskName;
        @JsonProperty("status")
        private String status;
        @JsonProperty("total_size")
        private Long totalSize;
        @JsonProperty("selected_size")
        private Long selectedSize;
        @JsonProperty("selected_downloaded_size")
        private Integer selectedDownloadedSize;
        @JsonProperty("download_rate")
        private Integer downloadRate;
        @JsonProperty("upload_rate")
        private Integer uploadRate;
        @JsonProperty("error_code")
        private String errorCode;
        @JsonProperty("error_message")
        private String errorMessage;
        @JsonProperty("permillage")
        private Integer permillage;
        @JsonProperty("left_time")
        private String leftTime;
    }
}
