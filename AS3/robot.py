from abc import ABC, abstractmethod
from typing import Tuple


class Robot(ABC):

    def __init__(self, controller=None):

        self.controller = controller

        self.state = None

        self.world_pose: Tuple[float, float] = (0.0, 0.0)


    @abstractmethod
    def setup(self):
        return

    @abstractmethod
    def update(self):
        return

    @abstractmethod
    def terminate(self):
        return

    @abstractmethod
    def odom_reset(self):
        """
        set the robot’s current location to 0,0,
        """

    @abstractmethod
    def odom_update(self):
        """
        uses robot measurements to update odometry.
        """

