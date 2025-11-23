const signUp = document.getElementById('signup');
const login = document.getElementById('login');
const container = document.getElementById('container');

signUp.addEventListener('click', () => {
    container.classList.add('right-panel-active');
})
login.addEventListener('click', () => {
    container.classList.remove('right-panel-active');
});