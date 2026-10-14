# extract_first_frame.py
import cv2

video = cv2.VideoCapture("splash_video.mp4")
ret, frame = video.read()
if ret:
    cv2.imwrite("splash_first_frame.png", frame)
    print("First frame saved!")
video.release()