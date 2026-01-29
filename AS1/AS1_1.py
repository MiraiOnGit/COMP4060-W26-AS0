import time
import random
from epuck import EPuckCom, EPuckIP, epuck   #makesure you have pyserial installed
import As1lib

import numpy as np
from PIL import Image
import matplotlib.pyplot as plt
import AS1_3 as ik




R_MAX_SPEED = 1000 #step/s


def move_steps(epuckcomm, l_speed_steps_s, r_speed_steps_s, l_target_steps, r_target_steps,Hz=10): 
    # Sets the robot’s left and right wheel speed as given (in steps/s)
    # starts a control loop 
        # monitors the robot odometry readings to see how far (in motor steps) the robot has gone
        # if both the left and right targets (l_target_steps, r_target_steps) were met
            # stop 
    # return (left_steps_moved, right_steps_moved)  

    epuckcomm.data_update()
    prev_left = epuckcomm.state.sens_left_motor_steps
    prev_right = epuckcomm.state.sens_right_motor_steps
    total_left = total_right = 0

    # start motor
    epuckcomm.state.act_left_motor_speed = l_speed_steps_s
    epuckcomm.state.act_right_motor_speed = r_speed_steps_s
    epuckcomm.send_command()
    epuckcomm.data_update()
    time.sleep(0.5)  #give time for the robot to get the request

    left_reached = False
    right_reached = False
    while not (left_reached and right_reached):
        epuckcomm.data_update()
        current_left = epuckcomm.state.sens_left_motor_steps
        current_right = epuckcomm.state.sens_right_motor_steps
        total_left += As1lib.steps_delta(prev_left, current_left)
        print(total_left)
        total_right += As1lib.steps_delta(prev_right, current_right)
        print(total_right)
        prev_left = current_left
        prev_right = current_right
        if l_target_steps >= 0:
            if total_left >= l_target_steps:
                left_reached = True
        else:
            if total_left <= l_target_steps:
                left_reached = True
        if r_target_steps >= 0:
            if total_right >= r_target_steps:
                right_reached = True
        else:
            if total_right <= r_target_steps:
                right_reached = True
        time.sleep(1.0 / Hz)
    # epuckcomm.state.act_left_motor_speed = 0
    # epuckcomm.state.act_right_motor_speed = 0
    # epuckcomm.data_update()
    # epuckcomm.stop_all()
    return (total_left, total_right)
   
def move_straight (epuckcomm, distance_mm, speed_mm_s, Hz=10):
    target_steps = As1lib.mm_to_steps(distance_mm)
    speed_steps_s = As1lib.mm_to_steps(speed_mm_s)
    actual_steps = move_steps(
        epuckcomm,
        int(speed_steps_s),     # Left wheel speed
        int(speed_steps_s),     # Right wheel speed  
        int(target_steps),      # Left wheel target
        int(target_steps),      # Right wheel target
        Hz=Hz
    )
    actual_left_mm = As1lib.steps_to_mm(actual_steps[0])
    actual_right_mm = As1lib.steps_to_mm(actual_steps[1])
    actual_distance_mm = (actual_left_mm + actual_right_mm) / 2.0
    
    return actual_distance_mm

def epuck_test():

    # epuckcomm = EPuckCom("COM25", debug=False)
    epuckcomm = EPuckIP("192.168.137.150", debug=True) # CSHRI
    
    if (not epuckcomm.connect()):
        print("Could not connect, quitting")
        return

    epuckcomm.enable_sensors = True
    epuckcomm.send_command() # enable sensor stream.
    time.sleep(2)  #give time for the robot to get the request
    
    
    # move the robot forward about 13cm
    # move_steps(epuckcomm, R_MAX_SPEED, R_MAX_SPEED, 1000, 1000) 

    # move the robot backward about 13cm
    # move_steps(epuckcomm, -R_MAX_SPEED, -R_MAX_SPEED, -1000, -1000)

    # move the robot on the spot, clockwise, about one rotation
    # move_steps(epuckcomm, R_MAX_SPEED,-R_MAX_SPEED, 1290, -1290)

    # make the robot turn right, pivoting on the right wheel, about one rotation
    # move_steps(epuckcomm, R_MAX_SPEED, 0, 2580, 0)

    # move the robot forward by 1m, then move back roughly to the same spot
    # move_straight (epuckcomm, 1000, 1000)
    # move_straight (epuckcomm, -1000, -1000)

    # (-432.91910856513636, -166.2524418984697, 3246.8933142385226, 1246.8933142385226)
    # move_steps(epuckcomm, *ik.diff_drive_inverse_kin(200, 100, 0), Hz=10)
    # epuckcomm.send_command() # enable sensor stream.
    # time.sleep(0.1)  #give time for the robot to get the request
   
    move_steps(epuckcomm, *ik.diff_drive_inverse_kin(0, 100, np.pi/2), Hz=100)
    epuckcomm.send_command() # enable sensor stream.
    time.sleep(0.1)  #give time for the robot to get the request

    # move_steps(epuckcomm, *ik.diff_drive_inverse_kin(200, 100, 0), Hz=10)
    # epuckcomm.send_command() # enable sensor stream.
    # time.sleep(0.1)  #give time for the robot to get the request

    # move_steps(epuckcomm, *ik.diff_drive_inverse_kin(0, 100, -np.pi/2), Hz=10)
    # epuckcomm.send_command() # enable sensor stream.
    # time.sleep(0.5)  #give time for the robot to get the request

    # move_steps(epuckcomm, *ik.diff_drive_inverse_kin(200, 100, 0), Hz=10)
    # epuckcomm.send_command() # enable sensor stream.
    # time.sleep(0.5)  #give time for the robot to get the request

    # move_steps(epuckcomm, *ik.diff_drive_inverse_kin(0, 100, -np.pi/2), Hz=10)
    # epuckcomm.send_command() # enable sensor stream.
    # time.sleep(0.5)  #give time for the robot to get the request

    # move_steps(epuckcomm, *ik.diff_drive_inverse_kin(200, 100, 0), Hz=10)
    # epuckcomm.send_command() # enable sensor stream.
    # time.sleep(0.1)  #give time for the robot to get the request

    # move_steps(epuckcomm, *ik.diff_drive_inverse_kin(0, 100, -np.pi/2), Hz=10)
    # epuckcomm.send_command() # enable sensor stream.
    # time.sleep(0.5)  #give time for the robot to get the request

    

    

   

    epuckcomm.stop_all()
    epuckcomm.close()
    
    
epuck_test()