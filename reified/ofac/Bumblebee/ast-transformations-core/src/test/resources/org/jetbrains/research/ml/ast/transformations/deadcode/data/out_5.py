import random


def main():
    while True:
        print("Hello")


def foo():
    while True:
        if random.randint(1, 10) % 2 == 0:
            break
        print("Hello")
    print(1)
    print(2)
    print(3)
