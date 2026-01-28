import time
import random
from epuck import EPuckCom, EPuckIP, epuck   #makesure you have pyserial installed
import As1lib

import numpy as np
from PIL import Image
import matplotlib.pyplot as plt


# def cam_bytes_to_image(mode, data, width, height):

#     if (mode == epuck.CAM_MODE_RGB565):
#         npdata = np.frombuffer(data, dtype=">i2")    # camera is big endian, 16 bit per pixel.
#         npdata = npdata.astype(np.uint32)            #expand to 32 bit to make room for unpacking.
#         alpha = 0xFF000000     #ALPHA is MSB, all set to 1
#         r = ((npdata & 0xF800) >> 8)    # mask out top 5 bits, then shift right to make it the LSB of the 32 bit
#         g = ((npdata & 0x07E0) << 5)         # mask out middle 6 bits, then shift a little left to make it the 2nd LSB
#         b = ((npdata & 0x001F) << 19)        # mask out the bottom 5 bits, then shift it all the way left to be the 3rd LSB
#         arr = alpha + r + g + b
#         return Image.frombuffer('RGBA', (width, height), arr, 'raw', 'RGBA', 0, 1)  

#     if (mode == epuck.CAM_MODE_GREY):
#         npdata = np.frombuffer(data, np.uint8)    # camera is big endian, 16 bit per pixel.
#         return Image.frombuffer('L', (width, height), npdata, 'raw', 'L', 0, 1)


def move_steps(epuckcomm, l_speed_steps_s, r_speed_steps_s, l_target_steps, r_target_steps,Hz=10): 
    # Sets the robot’s left and right wheel speed as given (in steps/s)
    # starts a control loop 
        # monitors the robot odometry readings to see how far (in motor steps) the robot has gone
        # if both the left and right targets (l_target_steps, r_target_steps) were met
            # stop 
    # return (left_steps_moved, right_steps_moved)    
    initial_left = epuckcomm.state.sens_left_motor_steps
    initial_right = epuckcomm.state.sens_left_motor_steps
    prev_left = initial_left
    prev_right = initial_right
    epuckcomm.state.act_left_motor_speed = l_speed_steps_s
    epuckcomm.state.act_right_motor_speed = r_speed_steps_s
    epuckcomm.send_command()
    epuckcomm.data_update()
    time.sleep(0.5)  #give time for the robot to get the request
    total_left = 0
    total_right = 0
    left_reached = False
    right_reached = False
    while not (left_reached and right_reached):
        current_left = epuckcomm.state.sens_left_motor_steps
        current_right = epuckcomm.state.sens_left_motor_steps
        delta_left = As1lib.steps_delta(initial_left, current_left)
        delta_right = As1lib.steps_delta(initial_right, current_right)
        total_left += delta_left
        total_right += delta_right
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
        
        prev_left = current_left
        prev_right = current_right
        time.sleep(1.0 / Hz)
    time.sleep(5)
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
    #epuckcomm.enable_camera = True
    epuckcomm.send_command() # enable sensor stream.
    time.sleep(0.5)  #give time for the robot to get the request

    # epuckcomm.set_camera_parameters(epuck.CAM_MODE_RGB565, 40, 40, 1)
    # epuckcomm.set_camera_parameters(EPuckComm.CAM_MODE_GREY, 40, 40, 8)
    # epuckcomm.get_camera_parameters()
    # print(epuckcomm.state.cam_framebytes)

    # epuckcomm.state.act_speaker_sound = epuck.SOUND_STARWARS
    # epuckcomm.send_command()
    # time.sleep(5)

    # for i in range(100):
    #     # epuckcomm.state.act_binary_led_states[random.randint(0,epuck.BINARY_LED_COUNT-1)] = random.randint(0,1)
    #     # epuckcomm.state.act_rgb_led_colors[random.randint(0,epuck.RGB_LED_COUNT-1)] = (random.randint(0,100), random.randint(0,100), random.randint(0,100))
    #     epuckcomm.state.act_left_motor_speed = -1200
    #     epuckcomm.state.act_right_motor_speed = 1200
    #     epuckcomm.send_command()
    #     epuckcomm.data_update()
        
    #     if (epuckcomm.enable_camera):
    #         im = cam_bytes_to_image(epuckcomm.cam_mode, epuckcomm.sens_framebuffer, epuckcomm.cam_width, epuckcomm.cam_height)
    #         if (epuckcomm.cam_mode == epuck.CAM_MODE_GREY):
    #             plt.imshow(im, cmap='gray', vmin=0, vmax=255)
    #         elif (epuckcomm.cam_mode == epuck.CAM_MODE_RGB565): 
    #             plt.imshow(im)
    #         plt.pause(0.000001)
        
    #     print(str(epuckcomm.state.sens_tof_distance_mm) + " steps L/R: "+ str(epuckcomm.state.sens_left_motor_steps) + "/" + str(epuckcomm.state.sens_right_motor_steps))
    #     time.sleep(0.1) #100hz roughly
    time.sleep(2)

    epuckcomm.stop_all()
    epuckcomm.close()
    
    
epuck_test()