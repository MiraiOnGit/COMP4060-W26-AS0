import time
import random
from epuck import EPuckCom, EPuckIP, epuck   #makesure you have pyserial installed
import As1lib

import numpy as np
from PIL import Image
import matplotlib.pyplot as plt

R_MAX_SPEED = 1200 #step/s

def diff_drive_forward_kin( pose, left_steps, right_steps):
        pose = np.asarray(pose, dtype=float)
        r_x, r_y, r_theta = pose[:, 0], pose[:, 1], pose[:, 2]


        dR = np.asarray(As1lib.steps_to_mm(right_steps), dtype=float)
        dL = np.asarray(As1lib.steps_to_mm(left_steps), dtype=float)
       
        turn_angle = (dR - dL)/As1lib.WHEEL_BASE_MM
        turn_radius = (As1lib.WHEEL_BASE_MM*(dR+dL))/(2*(dR - dL))
        # ICC coordinates
        icc = np.stack([
            r_x - turn_radius * np.sin(turn_angle),
            r_y + turn_radius * np.cos(turn_angle)
        ], axis=1) 
        p = np.array([r_x, r_y])

        # translate
        new_origin = p - icc

        # rotate
        cos, sin = np.cos(turn_angle), np.sin(turn_angle)
        rotation_matrix = np.array([[cos, -sin],[sin,  cos]])
        rotation_result = np.dot(rotation_matrix, new_origin)

        # translate back
        new_pose = icc + rotation_result
        new_theta = r_theta + turn_angle

        return np.array([new_pose[0], new_pose[1], new_theta])




def epuck_test():

    # epuckcomm = EPuckCom("COM25", debug=False)
    epuckcomm = EPuckIP("192.168.137.150", debug=True) # CSHRI
    
    if (not epuckcomm.connect()):
        print("Could not connect, quitting")
        return

    epuckcomm.enable_sensors = True
    epuckcomm.send_command() # enable sensor stream.
    time.sleep(0.5)  #give time for the robot to get the request
    
    
    As1lib.print_pose(diff_drive_forward_kin( (0, 0, 0), 0, 0)) # should give: (0, 0, 0)
    As1lib.print_pose(diff_drive_forward_kin( (10, 20, 0), 1250, 1250)) # should give: (177,20, 0)
    As1lib.print_pose(diff_drive_forward_kin( (10, 20, np.pi/2), 1250, 1250)) # should give: (10, 187, 90)
    As1lib.print_pose(diff_drive_forward_kin( (0, 0, 0), -1250, 1250)) #should give (0, 0, 0)
    As1lib.print_pose(diff_drive_forward_kin( (0, 0, np.pi/2), 1250, -1250)) #should give (0,0, 90)
    As1lib.print_pose(diff_drive_forward_kin( (0, 0, 0), 2500, 0)) #should give (0, 0, 0)
    As1lib.print_pose(diff_drive_forward_kin( (1000, 1000, np.pi/2), 1250, -1250)) #should give (1000, 1000, 90)
    As1lib.print_pose(diff_drive_forward_kin( (0, 0, np.pi/2), 1250, 100)) #should give (61,7, 284)
    As1lib.print_pose(diff_drive_forward_kin( (0, 0, 0), 1991, 2075)) #should give (269, 29, 12)
    As1lib.print_pose(diff_drive_forward_kin( (0, 0, 0), 189, 2422)) #should give (-18, 6, 322)
    As1lib.print_pose(diff_drive_forward_kin( (0, 0, 0), 1249, 2598)) #should give (-19, 149, 194)
    epuckcomm.send_command() # enable sensor stream.
    # time.sleep(0.5)  #give time for the robot to get the request

   
    time.sleep(2)

    epuckcomm.stop_all()
    epuckcomm.close()
    
    
epuck_test()