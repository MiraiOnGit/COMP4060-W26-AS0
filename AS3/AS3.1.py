import controller
import robot_epuck
import planner_move_once
import navigator_diff_simple
import math

robot = robot_epuck.RobotEPuck("COM25")
planner = planner_move_once.PlannerMoveOnce((300, 1, math.pi/2))
navigator = navigator_diff_simple.NavigatorDiffSimple()

controller = controller.Controller()
controller.setup(planner, navigator, robot, hz=20)
controller.start()  #blocking until done
controller.terminate()
print("Controller exited")
