#!/usr/bin/env python3
"""
extract_pose_timeline.py
========================
Extract MediaPipe PoseLandmarker world landmarks from a video and save
them as a JSON timeline. No angle conversion — raw landmarks only.

macOS quick-start
-----------------
    pip3 install mediapipe opencv-python
    python3 extract_pose_timeline.py input.mp4 --auto-download

Usage
-----
    python3 extract_pose_timeline.py input.mp4
    python3 extract_pose_timeline.py input.mp4 -o out.json
    python3 extract_pose_timeline.py input.mp4 --auto-download
    python3 extract_pose_timeline.py input.mp4 --frame-step 2

Output JSON schema
------------------
{
  "meta": {
    "source": "input.mp4",
    "fps": 30.0,
    "total_frames": 900,
    "frame_step": 1,
    "duration_ms": 30000.0,
    "generated_at": "2025-06-09T12:00:00",
    "platform": "macOS 14.x (arm64)",
    "landmark_count": 33
  },
  "frames": [
    {
      "frame_index": 0,
      "timestamp_ms": 0.0,
      "detected": true,
      "landmarks": [
        { "index": 0,  "name": "nose",            "x": 0.012, "y": -0.45, "z": 0.003 },
        { "index": 1,  "name": "left_eye_inner",  "x": ...,   "y": ...,   "z": ... },
        ...
        { "index": 32, "name": "right_foot_index","x": ...,   "y": ...,   "z": ... }
      ]
    },
    ...
  ]
}

Landmark index reference (MediaPipe 33-point model)
----------------------------------------------------
  0  nose                  1  left_eye_inner
  2  left_eye              3  left_eye_outer
  4  right_eye_inner       5  right_eye
  6  right_eye_outer       7  left_ear
  8  right_ear             9  mouth_left
 10  mouth_right          11  left_shoulder
 12  right_shoulder       13  left_elbow
 14  right_elbow          15  left_wrist
 16  right_wrist          17  left_pinky
 18  right_pinky          19  left_index
 20  right_index          21  left_thumb
 22  right_thumb          23  left_hip
 24  right_hip            25  left_knee
 26  right_knee           27  left_ankle
 28  right_ankle          29  left_heel
 30  right_heel           31  left_foot_index
 32  right_foot_index
"""

import argparse
import json
import os
import platform
import sys
import urllib.request
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

# ── Landmark names ────────────────────────────────────────────────────────────

LANDMARK_NAMES = [
    "nose", "left_eye_inner", "left_eye", "left_eye_outer",
    "right_eye_inner", "right_eye", "right_eye_outer",
    "left_ear", "right_ear", "mouth_left", "mouth_right",
    "left_shoulder", "right_shoulder", "left_elbow", "right_elbow",
    "left_wrist", "right_wrist", "left_pinky", "right_pinky",
    "left_index", "right_index", "left_thumb", "right_thumb",
    "left_hip", "right_hip", "left_knee", "right_knee",
    "left_ankle", "right_ankle", "left_heel", "right_heel",
    "left_foot_index", "right_foot_index",
]

# ── Model download ────────────────────────────────────────────────────────────

MODEL_URL = (
    "https://storage.googleapis.com/mediapipe-models/"
    "pose_landmarker/pose_landmarker_full/float16/latest/"
    "pose_landmarker_full.task"
)
DEFAULT_MODEL = "pose_landmarker_full.task"


def download_model(dest: str) -> None:
    print(f"Downloading model → {dest}")
    def _prog(count, block, total):
        if total > 0:
            print(f"  {min(count*block/total*100,100):.0f}%", end="\r", flush=True)
    urllib.request.urlretrieve(MODEL_URL, dest, reporthook=_prog)
    print(f"\nSaved: {dest}  ({os.path.getsize(dest)/1_048_576:.1f} MB)")


# ── Main extraction ───────────────────────────────────────────────────────────

def extract(video_path: str, output_path: str, model_path: str,
            frame_step: int, auto_download: bool) -> None:

    video_path  = os.path.expanduser(video_path)
    output_path = os.path.expanduser(output_path)
    model_path  = os.path.expanduser(model_path)

    if not os.path.exists(video_path):
        sys.exit(f"Video not found: {video_path}")

    if not os.path.exists(model_path):
        if auto_download:
            download_model(model_path)
        else:
            sys.exit(
                f"Model not found: {model_path}\n"
                f"Re-run with --auto-download, or:\n"
                f"  curl -L -o {model_path} \"{MODEL_URL}\""
            )

    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        sys.exit(f"Cannot open video: {video_path}")

    fps          = cap.get(cv2.CAP_PROP_FPS) or 30.0
    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    duration_ms  = total_frames / fps * 1000.0
    plat         = f"{platform.system()} {platform.mac_ver()[0] or platform.release()} ({platform.machine()})"

    print(f"Video    : {video_path}")
    print(f"FPS      : {fps:.2f}   Frames: {total_frames}   Duration: {duration_ms/1000:.2f}s")
    print(f"Step     : every {frame_step} frame(s)")
    print()

    opts = mp_vision.PoseLandmarkerOptions(
        base_options=mp_python.BaseOptions(model_asset_path=model_path),
        running_mode=mp_vision.RunningMode.VIDEO,
        num_poses=1,
        min_pose_detection_confidence=0.5,
        min_pose_presence_confidence=0.5,
        min_tracking_confidence=0.5,
    )

    frames_out = []
    frame_idx  = 0
    detected   = 0

    with mp_vision.PoseLandmarker.create_from_options(opts) as detector:
        while True:
            ok, bgr = cap.read()
            if not ok:
                break

            if frame_idx % frame_step == 0:
                ts_ms    = frame_idx / fps * 1000.0
                rgb      = cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB)
                mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)
                result   = detector.detect_for_video(mp_image, int(ts_ms))

                if result.pose_world_landmarks:
                    lms = result.pose_world_landmarks[0]
                    landmarks = [
                        {
                            "index": i,
                            "name":  LANDMARK_NAMES[i],
                            "x":     round(lms[i].x, 6),
                            "y":     round(lms[i].y, 6),
                            "z":     round(lms[i].z, 6),
                        }
                        for i in range(len(lms))
                    ]
                    frames_out.append({
                        "frame_index":  frame_idx,
                        "timestamp_ms": round(ts_ms, 3),
                        "detected":     True,
                        "landmarks":    landmarks,
                    })
                    detected += 1
                else:
                    frames_out.append({
                        "frame_index":  frame_idx,
                        "timestamp_ms": round(ts_ms, 3),
                        "detected":     False,
                        "landmarks":    [],
                    })

                if frame_idx % 60 == 0:
                    pct = frame_idx / max(total_frames, 1) * 100
                    print(f"  {pct:5.1f}%  frame {frame_idx}/{total_frames}  detected={detected}",
                          end="\r", flush=True)

            frame_idx += 1

    cap.release()
    missed = len(frames_out) - detected
    print(f"\nDone — {detected} detected, {missed} missed.")

    output = {
        "meta": {
            "source":         os.path.basename(video_path),
            "fps":            fps,
            "total_frames":   total_frames,
            "frame_step":     frame_step,
            "duration_ms":    round(duration_ms, 3),
            "generated_at":   datetime.now().isoformat(timespec="seconds"),
            "platform":       plat,
            "landmark_count": len(LANDMARK_NAMES),
        },
        "frames": frames_out,
    }

    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(output, f, indent=2)

    print(f"Saved {len(frames_out)} frames → {output_path}  ({os.path.getsize(output_path)/1024:.1f} KB)")


# ── CLI ───────────────────────────────────────────────────────────────────────

def main():
    p = argparse.ArgumentParser(
        description="Extract MediaPipe PoseLandmarker world landmarks from a video to JSON."
    )
    p.add_argument("video", help="Input video file (mp4, mov, avi…)")
    p.add_argument("-o", "--output", default=None,
                   help="Output JSON path (default: <video_stem>_pose.json)")
    p.add_argument("--model", default=DEFAULT_MODEL,
                   help=f"Path to .task model (default: {DEFAULT_MODEL})")
    p.add_argument("--auto-download", action="store_true",
                   help="Download the model automatically if not found")
    p.add_argument("--frame-step", type=int, default=1, metavar="N",
                   help="Process every Nth frame (default: 1)")
    args = p.parse_args()

    out = args.output or os.path.splitext(os.path.expanduser(args.video))[0] + "_pose.json"
    extract(args.video, out, args.model, args.frame_step, args.auto_download)


if __name__ == "__main__":
    main()