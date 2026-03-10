import math
import time
import random
from epuck import EPuckCom, EPuckIP, epuck   #makesure you have pyserial installed


import numpy as np
from PIL import Image
import matplotlib.pyplot as plt

# constant

STEPS_PER_CYCLE = 50*20  # Motor steps for one full wheel rotation. 50:1 gear reduction * 20 steps/revolution at motor shaft
WHEEL_DIAMETER_MM = 41     # Diameter of the e-puck wheel in mm
WHEEL_RADIUS_MM = WHEEL_DIAMETER_MM / 2.0  # Radius in mm
WHEEL_BASE_MM = 52.0         # Distance between left and right wheels in mm
MAX_STEP_COUNT = 2**16       #  16-bit step counter - 2^16 = 65536

# WHEEL_BASE_MM = 53   # Jim value

def steps_delta(last, current):
    # int
    # calculates the difference in robot steps from the last position to the current, 
    # accounting for counter wraparound, 
    # returns it as a signed integer.

    delta = current - last
    if delta > (MAX_STEP_COUNT / 2):  return delta - MAX_STEP_COUNT
    if delta < (-MAX_STEP_COUNT / 2): return delta + MAX_STEP_COUNT
    return delta

def steps_to_rad(steps):
    # float
    # converts signed motor steps to signed radians 
    # returns that value, using your knowledge of the motor construction. 
    
    rad = (2*math.pi)  * (steps/ STEPS_PER_CYCLE)
    return rad


def rad_to_steps(rad):
    # float
    # converts signed radians to signed motor steps
    # returns that value, using your knowledge of the motor construction

    steps = (rad *STEPS_PER_CYCLE) / (2*math.pi)
    return steps


def rad_to_mm(rad):
    # float
    # converts the given signed radians of wheel rotation into expected signed ground distance 
    # returns that value. 

    mm = WHEEL_RADIUS_MM * rad
    return mm


def mm_to_rad(mm):
    # float
    # converts the given signed mm distance into expected signed radians of wheel rotation 
    # returns that value. 

    rad = mm / WHEEL_RADIUS_MM
    return rad


def steps_to_mm(steps):
    # float
    # converts motor steps into expected ground distance, all signed,  
    # returns that value. (use above functions) 

    mm = rad_to_mm(steps_to_rad(steps))
    return mm


def mm_to_steps(mm):
    # float
    # converts expected ground distance into motor steps, signed, 
    # returns that value. 

    steps = rad_to_steps(mm_to_rad(mm))
    return steps


def print_pose (pose):
    # prints x,y,theta while converting theta to degrees.
    
    x_mm, y_mm, theta_rad = pose
    theta_deg = math.degrees(theta_rad) % 360
    print(f"Pose: x: {x_mm} mm, y: {y_mm} mm, deg: {theta_deg:}°")

def move_steps(epuckcomm, l_speed_steps_s, r_speed_steps_s, l_target_steps, r_target_steps,Hz=10): 

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
        total_left += steps_delta(prev_left, current_left)
        total_right += steps_delta(prev_right, current_right)
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
        if (left_reached and right_reached):
            break
        time.sleep(1.0 / Hz)
    epuckcomm.state.act_left_motor_speed = 0
    epuckcomm.state.act_right_motor_speed = 0
    # epuckcomm.data_update()
    epuckcomm.stop_all()
    return (total_left, total_right)
   
def move_straight (epuckcomm, distance_mm, speed_mm_s, Hz=10):
    target_steps = mm_to_steps(distance_mm)
    speed_steps_s = mm_to_steps(speed_mm_s)
    actual_steps = move_steps(
        epuckcomm,
        speed_steps_s,     # Left wheel speed
        speed_steps_s,     # Right wheel speed  
        target_steps,      # Left wheel target
        target_steps,      # Right wheel target
        Hz=Hz
    )
    actual_left_mm = steps_to_mm(actual_steps[0])
    actual_right_mm = steps_to_mm(actual_steps[1])
    actual_distance_mm = (actual_left_mm + actual_right_mm) / 2.0
    
    return actual_distance_mm

    
def diff_drive_forward_kin( pose, left_steps, right_steps):
        # pose = np.asarray(pose, dtype=float)
        r_x, r_y, r_theta = pose

        dR = steps_to_mm(right_steps)
        dL = steps_to_mm(left_steps)
        if abs(dR - dL) == 0:
            distance = (dR + dL) / 2.0
            new_x = r_x + distance * np.cos(r_theta)
            new_y = r_y + distance * np.sin(r_theta)
            new_theta = r_theta
        else:
            turn_angle = (dR - dL)/WHEEL_BASE_MM
            turn_radius = (WHEEL_BASE_MM*(dR+dL))/(2*(dR - dL))

            # ICC coordinates
            icc = np.array([
                r_x - turn_radius * np.sin(r_theta),
                r_y + turn_radius * np.cos(r_theta)
            ]) 
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
            new_x, new_y = new_pose[0], new_pose[1]

        return (new_x, new_y, new_theta)
        
def diff_drive_inverse_kin(distance_mm, speed_mm_s, omega_rad):
    # The distance_mm and speed_mm should have matched signs (negative means moving backward). 
    # Returns how many steps (signed) each wheel should move at what speed. 
    # Does not move the robot at all, just performs calculations. 
    # Note the special case of turning on the spot (distance_mm == 0).
    # (left_steps_s, right_steps_s, left_steps, right_steps)
    if (distance_mm*speed_mm_s <0):
        return "Distance and speed must have same signed"
    if distance_mm == 0 and speed_mm_s == 0 and omega_rad == 0:
        return (0.0, 0.0, 0.0, 0.0)
    if (distance_mm == 0 and omega_rad != 0):
        left_mm = -omega_rad * As1lib.WHEEL_BASE_MM /2
        right_mm = omega_rad * As1lib.WHEEL_BASE_MM /2
        if speed_mm_s < 0: 
            left_mm  *= -1.0
            right_mm *= -1.0
        # Convert to steps
        left_steps = mm_to_steps(left_mm)
        right_steps = mm_to_steps(right_mm)
        speed_steps_s = mm_to_steps(speed_mm_s)
        left_steps_s = math.copysign(abs(speed_steps_s), left_steps)
        right_steps_s = math.copysign(abs(speed_steps_s), right_steps)
        return (left_steps_s, right_steps_s, left_steps, right_steps)         
    
    t_s = distance_mm / speed_mm_s
    turn_rate = omega_rad/t_s
    left_mm_s = speed_mm_s - turn_rate*WHEEL_BASE_MM /2
    left_steps_s = mm_to_steps(left_mm_s)
    right_mm_s = speed_mm_s + turn_rate*WHEEL_BASE_MM /2
    right_steps_s = mm_to_steps(right_mm_s)
    left_steps = left_steps_s*t_s
    right_steps = right_steps_s*t_s
    return (left_steps_s, right_steps_s, left_steps, right_steps)

if __name__ == "__main__":
    

    print(f"Motor steps per revolution: {STEPS_PER_CYCLE}")
    print(f"Wheel diameter: {WHEEL_DIAMETER_MM} mm")
    print(f"Wheel radius: {WHEEL_RADIUS_MM} mm")
    print(f"Wheel base: {WHEEL_BASE_MM} mm")
    

    # steps_delta
    delta = steps_delta(-1000, 16999)
    print(f"steps_delta(1000, 32000) = {delta}")

    steps = mm_to_steps(1000)
    halfsteps = mm_to_steps(500)
    print(f"mm_to_steps({1000:.4f}) = {steps:.2f} steps")
    print(f"mm_to_steps({500:.4f}) = {halfsteps:.2f} steps")

