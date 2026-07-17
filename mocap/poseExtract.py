import argparse
import csv
import os
import sys
from datetime import datetime
 
try:
    import cv2
except ImportError:
    sys.exit("opencv-python not found.\n  pip3 install opencv-python")
 
try:
    import mediapipe as mp
    from mediapipe.tasks import python as mp_python
    from mediapipe.tasks.python import vision as mp_vision
except ImportError:
    sys.exit("mediapipe not found.\n  pip3 install mediapipe")
 
# Only the landmarks needed for Sota joint angle calculation, in order
REQUIRED_LANDMARKS = [
    (0,  "nose"),
    (7,  "left_ear"),
    (8,  "right_ear"),
    (11, "left_shoulder"),
    (12, "right_shoulder"),
    (13, "left_elbow"),
    (14, "right_elbow"),
    (15, "left_wrist"),
    (16, "right_wrist"),
    (23, "left_hip"),
    (24, "right_hip"),
]
 
HEADER = (
    ["frame_index", "timestamp_ms", "detected"] +
    [f"{name}_{axis}" for _, name in REQUIRED_LANDMARKS for axis in ("x", "y", "z")]
)
 
ZEROS = [0.0] * (len(REQUIRED_LANDMARKS) * 3)
 
 
def extract(video_path, output_path, model_path, frame_step):
    video_path  = os.path.expanduser(video_path)
    output_path = os.path.expanduser(output_path)
    model_path  = os.path.expanduser(model_path)
 
    if not os.path.exists(video_path):
        sys.exit(f"Video not found: {video_path}")
    if not os.path.exists(model_path):
        sys.exit(f"Model not found: {model_path}")
 
    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        sys.exit(f"Cannot open video: {video_path}")
 
    fps          = cap.get(cv2.CAP_PROP_FPS) or 30.0
    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    duration_ms  = total_frames / fps * 1000.0
    frame_ms     = 1000.0 / fps
 
    print(f"Video : {video_path}")
    print(f"Model : {model_path}")
    print(f"FPS   : {fps:.4f}   Frames: {total_frames}   Duration: {duration_ms/1000:.3f}s")
    print(f"Step  : every {frame_step} frame(s)")
    print()
 
    opts = mp_vision.PoseLandmarkerOptions(
        base_options=mp_python.BaseOptions(model_asset_path=model_path),
        running_mode=mp_vision.RunningMode.VIDEO,
        num_poses=1,
        min_pose_detection_confidence=0.5,
        min_pose_presence_confidence=0.5,
        min_tracking_confidence=0.5,
    )
 
    frame_idx  = 0
    detected   = 0
    last_mp_ts = -1
 
    with open(output_path, "w", newline="", encoding="utf-8") as csvfile:
        writer = csv.writer(csvfile)
        writer.writerow(HEADER)
 
        with mp_vision.PoseLandmarker.create_from_options(opts) as detector:
            while True:
                container_ts = cap.get(cv2.CAP_PROP_POS_MSEC)
                ok, bgr = cap.read()
                if not ok:
                    break
 
                if frame_idx % frame_step == 0:
                    if container_ts > last_mp_ts:
                        ts_ms = container_ts
                    else:
                        ts_ms = max(last_mp_ts + 1, frame_idx * frame_ms)
                    last_mp_ts = ts_ms
 
                    rgb      = cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB)
                    mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)
                    result   = detector.detect_for_video(mp_image, int(ts_ms))
 
                    if result.pose_world_landmarks:
                        lms = result.pose_world_landmarks[0]
                        values = []
                        for idx, _ in REQUIRED_LANDMARKS:
                            values += [round(lms[idx].x, 6),
                                       round(lms[idx].y, 6),
                                       round(lms[idx].z, 6)]
                        writer.writerow([frame_idx, round(ts_ms, 3), True] + values)
                        detected += 1
                    else:
                        writer.writerow([frame_idx, round(ts_ms, 3), False] + ZEROS)
 
                    if frame_idx % 60 == 0:
                        pct = frame_idx / max(total_frames, 1) * 100
                        print(f"  {pct:5.1f}%  frame {frame_idx}/{total_frames}"
                              f"  t={ts_ms:.1f}ms  detected={detected}",
                              end="\r", flush=True)
 
                frame_idx += 1
 
    cap.release()
    total = frame_idx // frame_step
    print(f"\nDone — {detected} detected, {total - detected} missed.")
    print(f"Saved {total} rows → {output_path}  ({os.path.getsize(output_path)/1024:.1f} KB)")
 
 
def main():
    p = argparse.ArgumentParser(
        description="Extract required MediaPipe landmarks from a video to CSV for Sota."
    )
    p.add_argument("video")
    p.add_argument("-o", "--output", default=None)
    p.add_argument("--model", default="pose_landmarker_heavy.task")
    p.add_argument("--frame-step", type=int, default=1, metavar="N")
    args = p.parse_args()
 
    out = args.output or os.path.splitext(os.path.expanduser(args.video))[0] + "_pose.csv"
    extract(args.video, out, args.model, args.frame_step)
 
if __name__ == "__main__":
    main()