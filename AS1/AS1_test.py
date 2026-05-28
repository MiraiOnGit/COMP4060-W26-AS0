import time
import random
from epuck import EPuckCom, EPuckIP, epuck   #makesure you have pyserial installed
import As1lib

import numpy as np
from PIL import Image
import matplotlib.pyplot as plt
import AS1_3 as ik
import AS1_2 as fk
import AS1_1 as epuckmove
R_MAX_SPEED = 1000 #step/s

def epuck_test():

    # epuckcomm = EPuckCom("COM25", debug=False)
    epuckcomm = EPuckIP("192.168.137.150", debug=True) # CSHRI
    
    if (not epuckcomm.connect()):
        print("Could not connect, quitting")
        return

    epuckcomm.enable_sensors = True
    epuckcomm.send_command() # enable sensor stream.
    time.sleep(2)  #give time for the robot to get the request


    # ========================================================================================================
    # Task 1
    # hz = 10
    # distance_mm = -1400
    # speed_mm_s = -70

    # epuckmove.move_straight (epuckcomm, distance_mm, speed_mm_s, hz)
    # epuckcomm.send_command() 
    # time.sleep(0.5)  

    # ========================================================================================================
    # Task 2
    # hz = 10
    # l_speed_steps_s = 900
    # r_speed_steps_s = 100
    # l_target_steps = 1800   
    # r_target_steps = 100

    # x_mm = 10
    # y_mm = 5
    # theta_rad = np.pi/2
    # pose = (x_mm, y_mm, theta_rad) 

    # epuckmove.move_steps(epuckcomm, l_speed_steps_s, r_speed_steps_s, l_target_steps, r_target_steps, hz)
    # As1lib.print_pose(fk.diff_drive_forward_kin(pose, l_target_steps, r_target_steps))
    # epuckcomm.send_command() 
    # time.sleep(0.5)  

    # ========================================================================================================
    # Task 3
    hz = 30
    distance_mm = -500
    speed_mm_s = -60
    omega_rad = -np.pi/8


    epuckmove.move_steps(epuckcomm, *ik.diff_drive_inverse_kin(distance_mm, speed_mm_s, omega_rad), hz)
    epuckcomm.send_command() # enable sensor stream.
    time.sleep(0.5)  #give time for the robot to get the request
    
    epuckcomm.stop_all()
    epuckcomm.close()

epuck_test()