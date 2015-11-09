```
brew tap homebrew/science
brew install opencv
brew install ant
brew edit opencv
```

Add the following ```-DBUILD_opencv_java=ON``` to the args array.
```
brew install opencv
brew reinstall opencv --with-java
```

```
-Djava.library.path=/usr/local/Cellar/opencv/2.4.9/share/OpenCV/java
```
