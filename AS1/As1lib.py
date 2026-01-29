import math


# constant

STEPS_PER_CYCLE = 50*20  # Motor steps for one full wheel rotation. 50:1 gear reduction * 20 steps/revolution at motor shaft
WHEEL_DIAMETER_MM = 42.5     # Diameter of the e-puck wheel in mm
WHEEL_RADIUS_MM = WHEEL_DIAMETER_MM / 2.0  # Radius in mm
WHEEL_BASE_MM = 52.0         # Distance between left and right wheels in mm
MAX_STEP_COUNT = 2**16       #  16-bit step counter - 2^16 = 65536

# WHEEL_BASE_MM = 53   # Jim value

def steps_delta(last, current):
    # int
    # calculates the difference in robot steps from the last position to the current, 
    # accounting for counter wraparound, 
    # returns it as a signed integer.
    # 5 lines

    delta = current - last
    if delta > (MAX_STEP_COUNT / 2):  return delta - MAX_STEP_COUNT
    if delta < (-MAX_STEP_COUNT / 2): return delta + MAX_STEP_COUNT
    return delta

def steps_to_rad(steps):
    # float
    # converts signed motor steps to signed radians 
    # returns that value, using your knowledge of the motor construction. 
    # 2 lines
    
    rad = (2*math.pi)  * (steps/ STEPS_PER_CYCLE)
    return rad


def rad_to_steps(rad):
    # float
    # converts signed radians to signed motor steps
    # returns that value, using your knowledge of the motor construction
    # 2 lines

    steps = (rad *STEPS_PER_CYCLE) / (2*math.pi)
    return steps


def rad_to_mm(rad):
    # float
    # converts the given signed radians of wheel rotation into expected signed ground distance 
    # returns that value. 
    # 2 lines

    mm = WHEEL_RADIUS_MM * rad
    return mm


def mm_to_rad(mm):
    # float
    # converts the given signed mm distance into expected signed radians of wheel rotation 
    # returns that value. 
    # 2 lines

    rad = mm / WHEEL_RADIUS_MM
    return rad


def steps_to_mm(steps):
    # float
    # converts motor steps into expected ground distance, all signed,  
    # returns that value. (use above functions) 
    # 2 lines

    mm = rad_to_mm(steps_to_rad(steps))
    return mm


def mm_to_steps(mm):
    # float
    # converts expected ground distance into motor steps, signed, 
    # returns that value. 
    # 2 lines

    steps = rad_to_steps(mm_to_rad(mm))
    return steps


def print_pose (pose):
    # prints x,y,theta while converting theta to degrees.
    # 3 lines
    
    x_mm, y_mm, theta_rad = pose
    theta_deg = math.degrees(theta_rad) % 360
    print(f"Pose: x: {x_mm} mm, y: {y_mm} mm, deg: {theta_deg:}°")
    


if __name__ == "__main__":
    

    print(f"Motor steps per revolution: {STEPS_PER_CYCLE}")
    print(f"Wheel diameter: {WHEEL_DIAMETER_MM} mm")
    print(f"Wheel radius: {WHEEL_RADIUS_MM} mm")
    print(f"Wheel base: {WHEEL_BASE_MM} mm")
    

    # steps_delta
    delta = steps_delta(-1000, 16999)
    print(f"steps_delta(1000, 32000) = {delta}")

    # delta = steps_delta(65500, 100)
    # print(f"steps_delta(65500, 100) = {delta}")

    # delta = steps_delta(100, 65500)
    # print(f"steps_delta(100, 65500) = {delta}")

    # steps_to_rad
    # rad = steps_to_rad(1000)
    # print(f"steps_to_rad(1000) = {rad:.6f} rad")

    # # rad_to_steps
    # steps = rad_to_steps(2 * math.pi)
    # print(f"rad_to_steps(2pi) = {steps:.2f} steps")

    # # rad_to_mm
    # circumference = math.pi * WHEEL_DIAMETER_MM
    # distance = rad_to_mm(2 * math.pi)
    # print(f"rad_to_mm(2pi) = {distance:.4f} mm")

    # # mm_to_rad
    # rad = mm_to_rad(circumference)
    # print(f"mm_to_rad({circumference:.4f}) = {rad:.6f} rad")
    
    # # steps_to_mm
    # distance = steps_to_mm(1000)
    # print(f"steps_to_mm(1000) = {distance:.4f} mm")

    # steps = mm_to_steps(circumference)
    # print(f"mm_to_steps({circumference:.4f}) = {steps:.2f} steps")

