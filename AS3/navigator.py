from abc import ABC, abstractmethod


class Navigator(ABC):

    def __init__(self, controller=None):
        self.controller = controller

        self.has_hit_target: bool = False


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
    def set_target(self, distance_mm: float, omega_rad: float, speed_mm_s: float):
        self.has_hit_target = False