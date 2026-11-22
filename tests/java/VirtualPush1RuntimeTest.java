                                for (final IValueObserver<String> observer : cursorNameObservers)
                                    observer.update (virtualCursorDeviceName);
                            }));
                        return null;
                    case "toggleEnabledState":