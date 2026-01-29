import time
import random
from epuck import EPuckCom, EPuckIP, epuck   #makesure you have pyserial installed
import As1lib

import numpy as np
from PIL import Image
import matplotlib.pyplot as plt
import math

R_MAX_SPEED = 1000 #step/s



def diff_drive_inverse_kin(distance_mm, speed_mm_s, omega_rad):
    # The distance_mm and speed_mm should have matched signs (negative means moving backward). 
    # Returns how many steps (signed) each wheel should move at what speed. 
    # Does not move the robot at all, just performs calculations. 
    # Note the special case of turning on the spot (distance_mm == 0).
    # (left_steps_s, right_steps_s, left_steps, right_steps)
    if distance_mm == 0:
        left_mm = -omega_rad * As1lib.WHEEL_BASE_MM /2
        right_mm = omega_rad * As1lib.WHEEL_BASE_MM /2
        # Convert to steps
        left_steps = As1lib.mm_to_steps(left_mm)
        right_steps = As1lib.mm_to_steps(right_mm)
        speed_steps_s = As1lib.mm_to_steps(speed_mm_s)

        if speed_mm_s != 0:
            left_steps_s = math.copysign(abs(speed_steps_s), left_steps)
            right_steps_s = math.copysign(abs(speed_steps_s), right_steps)
        return (left_steps_s, right_steps_s, left_steps, right_steps)  
          
    else:
        t_s = distance_mm / speed_mm_s
        turn_rate = omega_rad/t_s
        left_mm_s = speed_mm_s - turn_rate*As1lib.WHEEL_RADIUS_MM
        left_steps_s = As1lib.mm_to_steps(left_mm_s)
        right_mm_s = speed_mm_s + turn_rate*As1lib.WHEEL_RADIUS_MM
        right_steps_s = As1lib.mm_to_steps(right_mm_s)
        left_steps = left_steps_s*t_s
        right_steps = right_steps_s*t_s
        return (left_steps_s, right_steps_s, left_steps, right_steps)


        

def epuck_test():
    
    
    # As1lib.print_pose(diff_drive_forward_kin( (0, 0, 0), 0, 0)) # should give: (0, 0, 0)
    # As1lib.print_pose(diff_drive_forward_kin( (10, 20, 0), 1250, 1250)) # should give: (177,20, 0)
    # As1lib.print_pose(diff_drive_forward_kin( (10, 20, np.pi/2), 1250, 1250)) # should give: (10, 187, 90)
    # As1lib.print_pose(diff_drive_forward_kin( (0, 0, 0), -1250, 1250)) #should give (0, 0, 0)
    # As1lib.print_pose(diff_drive_forward_kin( (0, 0, np.pi/2), 1250, -1250)) #should give (0,0, 90)
    # As1lib.print_pose(diff_drive_forward_kin( (0, 0, 0), 2500, 0)) #should give (0, 0, 0)
    # As1lib.print_pose(diff_drive_forward_kin( (1000, 1000, np.pi/2), 1250, -1250)) #should give (1000, 1000, 90)
    # As1lib.print_pose(diff_drive_forward_kin( (0, 0, np.pi/2), 1250, 100)) #should give (61,7, 284)
    # As1lib.print_pose(diff_drive_forward_kin( (0, 0, 0), 1991, 2075)) #should give (269, 29, 12)
    # As1lib.print_pose(diff_drive_forward_kin( (0, 0, 0), 189, 2422)) #should give (-18, 6, 322)
    # As1lib.print_pose(diff_drive_forward_kin( (0, 0, 0), 1249, 2598)) #should give (-19, 149, 194)
    print(diff_drive_inverse_kin(130, 10, 0)) # should give (75, 75, 974, 974)
    print(diff_drive_inverse_kin(-130, -10, 0)) # should give (-75, -75, -974, -974)
    print(diff_drive_inverse_kin(300, 50, 0)) # should give (374, 374, 2246, 2246)
    print(diff_drive_inverse_kin(200, 70, np.pi/4)) # should give (470, 579, 1342,1654)
    print(diff_drive_inverse_kin(-200, -70, -np.pi/4)) # should give (-470, -579, -1342, -1654)
    print(diff_drive_inverse_kin(-200, -70, np.pi/4)) # should give (-579, -470, -1654, -1342)
    print(diff_drive_inverse_kin(-300, -40, -np.pi*2)) # should give (-133, -467, -1000, -3494)
    print(diff_drive_inverse_kin(0, 100, -np.pi*2)) # should give (749, -749, 1247, -1247)
    print(diff_drive_inverse_kin(0, 50, np.pi/2)) # should give (-374, 374, -311, 311)
    print(diff_drive_inverse_kin(0, -50, np.pi/2)) # should give (374, -374, 311, -311)
    print(diff_drive_inverse_kin(0, -50, -np.pi/2)) # should give (-374, 374, -311, 311)
    print(diff_drive_inverse_kin(300, -40, -np.pi*2)) # should give (-374, 374, -311, 311)
    
    
    
epuck_test()